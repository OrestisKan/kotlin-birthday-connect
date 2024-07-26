import carriers.Carriers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.nio.channels.IllegalBlockingModeException
import java.util.concurrent.Executors
import kotlin.random.Random

private const val TAG = "ConnectionEstablisher"

private lateinit var myNATType: NATType
private lateinit var printJob: Deferred<Unit>
private lateinit var packetProcessJob: Deferred<Unit>
private lateinit var messageListenerJobs: MutableList<Deferred<Unit>>
val bdayAttackDispatcher = Executors.newSingleThreadExecutor {
        task -> Thread(task, "bday-thread")
}.asCoroutineDispatcher()
private var connectionsMap = mutableMapOf<String, InetSocketAddress>()
private var runningConnectionEstablishers = mutableMapOf<String, Deferred<Unit>>()
private lateinit var myUUID: String
val printChannel: Channel<String> = Channel(Channel.UNLIMITED)
val packetChannel: Channel<Pair<DatagramPacket, DatagramSocket>> = Channel(Channel.UNLIMITED)
var socketList = mutableListOf<DatagramSocket>()
class ConnectionEstablisher {
    private fun setup(myProvider: Carriers, uuid: String): List<Deferred<Unit>> {
        val printThread = Executors.newSingleThreadExecutor {
                task -> Thread(task, "print-thread")
        }.asCoroutineDispatcher()
        printJob = GlobalScope.async(printThread) {consumePrintMessages()}

        val packetProcessThread = Executors.newSingleThreadExecutor {
                task -> Thread(task, "packet-process-thread")
        }.asCoroutineDispatcher()
        packetProcessJob = GlobalScope.async(packetProcessThread) { consumePackets() }
        packetProcessJob.start()

        messageListenerJobs = setupListeners()

        myNATType = getNatType(myProvider)
        myUUID = uuid
        return mutableListOf(printJob, packetProcessJob) + messageListenerJobs
    }

    private fun setupListeners(): MutableList<Deferred<Unit>> {
        val mutableList = mutableListOf<Deferred<Unit>>()
        socketList.forEachIndexed { index, datagramSocket ->
            val messageListenerThread = Executors.newSingleThreadExecutor {
                    task -> Thread(task, "message-listener-thread-$index")
            }.asCoroutineDispatcher()
            val messageListenerJob = GlobalScope.async(messageListenerThread) { checkMessageReceived(datagramSocket) }
            messageListenerJob.start()
            mutableList += messageListenerJob
        }

        return mutableList
    }

    private suspend fun connect(theirProvider: Carriers, theirUUID: String, theirIPAddressString: String, theirPort:Int? = null): Deferred<Unit> = GlobalScope.async(bdayAttackDispatcher){
        val theirNATType = getNatType(theirProvider)
        val connectionJob = GlobalScope.async(bdayAttackDispatcher) {
            if(theirPort != null) {
                repeat(10) {
                    val udpSocket = socketList[0]
                    sendPacket(udpSocket, InetAddress.getByName(theirIPAddressString), theirPort, "CONNECTION-INIT:$myUUID")
                    delay(200)
                }
            }else {
                //todo figure out if birthday attack is needed etc
                // is it always birthday attack ?
                startBirthdayAttack(InetAddress.getByName(theirIPAddressString), theirProvider)
            }
        }
        runningConnectionEstablishers[theirUUID] = connectionJob
        runningConnectionEstablishers[theirUUID]?.start()
        runningConnectionEstablishers[theirUUID]?.await()
        repeat(60) {
            if(theirUUID in connectionsMap){
                val inetSocketAddress = connectionsMap[theirUUID]
                if (inetSocketAddress != null) {
                    sendToPrintChannel("Connected with user#$theirUUID on ${inetSocketAddress.address.hostAddress}:${inetSocketAddress.port} ")
                }
                return@repeat
            }
            delay(1000)
        }
        sendToPrintChannel("Done with connection attempt to $theirIPAddressString")
    }


    private suspend fun checkMessageReceived(udpSocket: DatagramSocket) {
        while (true) {
            val receiveBuffer = ByteArray(100)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            udpSocket.soTimeout = 5000 // timeout so it changes number!
            try {
                withContext(Dispatchers.IO) {
                    udpSocket.receive(receivePacket)
                }
                sendToPrintChannel("Packet Received!!")
                val pair = Pair(receivePacket, udpSocket)
                sendToPacketChannel(pair)
            } catch (e: Exception) {
//                sendToPrintChannel("No response received, looping..")
            }
        }
    }

    private suspend fun startBirthdayAttack(ipAddress: InetAddress, theirProvider: Carriers) {
        val portChooser = PortChooser(theirProvider)
        portChooser.setup()
        var attempts = 1
        val message = "CONNECTION-INIT:$myUUID" // todo
        while(attempts <= 243587) {
            val port = portChooser.getNextPort()
            val index = attempts % socketList.size //todo maybe -1
            val socket = socketList[index]
            sendPacket(socket, ipAddress, port, message)
            if(attempts % 15 ==0) delay(2)
            else if(attempts % 20000 == 0) sendToPrintChannel("Attempt #$attempts")
            attempts++
            //todo add Random big delays everyonce a whuile ???
        }
        sendToPrintChannel("Done with Birthday attack")
    }


    private fun processPacket(pair: Pair<DatagramPacket, DatagramSocket>) {
        val datagramPacket = pair.first
        val udpSocket = pair.second
        val address = datagramPacket.address!!
        val port = datagramPacket.port
        val message = datagramPacket.data.toString(Charsets.UTF_8)
        sendToPrintChannel("Received packet from ${address.hostAddress}:$port with contents: $message")
        with(message){
            when {
                contains("INIT") -> {
                    sendPacket(udpSocket, address, port, "CONNECTION-MAINTENANCE:$myUUID")
                    val uuid = extractUUID(message)
                    connectionsMap[uuid] = InetSocketAddress(address, port)
                    if(runningConnectionEstablishers[uuid]?.isActive == true)
                        sendToPrintChannel("Closing Connection Establisher to $uuid")
                        runningConnectionEstablishers[uuid]!!.cancel()

                }
                contains("CONNECTION-MAINTENANCE") -> {sendToPrintChannel("Received connection maintenance packet from ${address.hostAddress}:$port")}
                else -> {}
            }
        }

    }

    private fun sendPacket(socket: DatagramSocket, ipAddress: InetAddress, port: Int, message:String): Boolean {
        return try {
            val sendData = message.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, ipAddress, port)
            socket.send(sendPacket)
            true
        }catch (ex: Exception) {
            when(ex) { //todo change/ optimize
                is PortUnreachableException -> {ex.printStackTrace()}
                is IOException -> {ex.printStackTrace()}
                is SecurityException -> {ex.printStackTrace()}
                is IllegalBlockingModeException -> {ex.printStackTrace()}
                is IllegalArgumentException -> {ex.printStackTrace()}
                else -> {ex.printStackTrace()}
            }
            false
        }
    }

    private fun sendToPrintChannel(string: String) {
        GlobalScope.launch(Dispatchers.Unconfined) {
            try{
                printChannel.send(string)
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }



    private suspend fun consumePrintMessages() {
            printChannel.consumeEach {
                println("$TAG:  $it") //todo change to log
            }
    }


    private suspend fun sendToPacketChannel(pair: Pair<DatagramPacket, DatagramSocket>) {
        packetChannel.send(pair)
    }



    private suspend fun consumePackets() {
        packetChannel.consumeEach {
            processPacket(it)
        }
    }



    private fun extractUUID(input: String): String { //todo test this

        val regex = Regex(""".*CONNECTION-(?:INIT|MAINTENANCE):([A-Za-z0-9]+).*""")
        val matchResult = regex.find(input)
        return matchResult?.groups?.get(1)?.value ?: "unknown${Random.nextInt()}"
    }

    /**
     * Map storage of each carriers analyzed and their corresponding NAT Type
     */
    private fun getNatType(carrier: Carriers): NATType {
        return when(carrier) {
            Carriers.VodafoneNL -> NATType.RESTRICTED_CONE_NAT
            Carriers.KPN -> NATType.SYMMETRIC_NAT
//        Carriers.Odido -> {}
            Carriers.LycaMobileNL -> NATType.FULL_CONE
            Carriers.LebaraNL -> NATType.RESTRICTED_CONE_NAT
//        Carriers.OrangeFR -> {}
//        Carriers.SFR -> {}
            Carriers.OrangeBG -> NATType.SYMMETRIC_NAT
            Carriers.LycaMobileBG -> NATType.RESTRICTED_CONE_NAT
            Carriers.TeliaNO -> NATType.RESTRICTED_CONE_NAT
            Carriers.MyCallNO -> NATType.FULL_CONE
            else -> NATType.SYMMETRIC_NAT //todo remove when all else is tested
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start(myProvider: Carriers, uuid: String, peers: List<Peer>): List<InetSocketAddress?> {
        fillUpSocketQueue()
        val suspendList = mutableListOf<Deferred<Unit>>()
        val observableList = mutableListOf<Deferred<Unit>>()
        val res = mutableListOf<InetSocketAddress?>()
        val channelJobs = setup(myProvider, uuid)
        suspendList.addAll(channelJobs)
        for(peer in peers) {
            val deferred = connect(peer.carrier, peer.uuid, peer.ipAddress, peer.theirPort)
            suspendList.add(deferred)
            observableList.add(deferred)
        }


        val killThread = Executors.newSingleThreadExecutor {
                task -> Thread(task, "kill-thread")
        }.asCoroutineDispatcher()
        val killJob = GlobalScope.async(killThread) {checkIfAllConnectRequestsAreDone(observableList)}
        suspendList.add(killJob)

        suspendList.forEach {it.start()}

        try{
            suspendList.awaitAll()
        }catch (ex: Exception){ //needed to actually exit
            ex.printStackTrace()
        }

        for(peer in peers) {
            res.add(connectionsMap[peer.uuid])
        }
        println("Size of connections map! ${connectionsMap.size}")
        println("Is packet Channel empty ?${packetChannel.isEmpty} ${packetChannel.isClosedForReceive}")
        if(!packetChannel.isEmpty && !packetChannel.isClosedForReceive) {
            println("Im here in the not empty channel")
            val pair = packetChannel.receive()
            val packet = pair.first
            val receivedData = String(packet.data, 0, packet.length)
            val senderAddress = packet.address
            val senderPort = packet.port

            println("Received data: '$receivedData' from $senderAddress:$senderPort")
        }
        return res
    }

    private fun closeJobs() {
        if(::printJob.isInitialized && printJob.isActive) printJob.cancel()
        if(::packetProcessJob.isInitialized && packetProcessJob.isActive) packetProcessJob.cancel()
        for (socket in socketList) socket.close()
        if(::messageListenerJobs.isInitialized) {
            for(messageListenerJob in messageListenerJobs) {
                if(messageListenerJob.isActive)
                    messageListenerJob.cancel()
            }
        }

    }

    private suspend fun checkIfAllConnectRequestsAreDone(observableList: List<Deferred<Unit>>) {
        while(true) {
            if(observableList.none { x -> x.isActive }) break
            delay(1000)
        }
        println("Killing all active jobs!")
        closeJobs()
    }

    private fun clean() {
        // Warning do not use before user gets their results!
        connectionsMap = mutableMapOf()
        runningConnectionEstablishers = mutableMapOf()
        socketList = mutableListOf()
    }

    private fun fillUpSocketQueue() {
        for(i in 1..500) {
            socketList.add(DatagramSocket())
        }
    }
}

enum class NATType {
    FULL_CONE, RESTRICTED_CONE_NAT, PORT_RESTRICTED_CONE_NAT, SYMMETRIC_NAT
}

class Peer(val carrier: Carriers, val uuid: String, val ipAddress: String, val theirPort: Int?=null)

//suspend fun main() {
//    val connectionEstablisher = ConnectionEstablisher()
//    val socket = withContext(NonCancellable) {
//        DatagramSocket()
//    }
//    val myCarrier = Carriers.VodafoneNL
//    val myUUID = UUID.randomUUID()
//    val uuid = UUID.fromString("4960d073-4982-4e02-8d16-5ae83a05a99e")
//    val peer1 = Peer(Carriers.Test, uuid, "130.161.119.223")
//
//    val result = connectionEstablisher.start(socket, myCarrier, myUUID, listOf(peer1))
//
//
//    result.forEach { println("Job1 $it") } //todo prints but still not existing? Why???
//
//}
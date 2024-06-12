import carriers.Carriers
import carriers.KPN
import carriers.LebaraNL
import carriers.ObservableQueue
import carriers.VodafoneNl
import kotlinx.coroutines.delay

class PortChooser(private val carrierName: Carriers) {
    private lateinit var queue: ObservableQueue<Int>
    private val numberOfItemsToGenerate = 10240
    private val threshold = 5000

    fun setup() {
        val fillFunction: () -> List<Int>
        when (carrierName) {
            Carriers.VodafoneNL -> {
                fillFunction = {
                    List(numberOfItemsToGenerate) {
                        VodafoneNl.sampleBeta()
                    }
                }
            }
            Carriers.LebaraNL -> {
                fillFunction = {
                    LebaraNL.getPortsList(numberOfItemsToGenerate)
                }
            }
            Carriers.KPN -> {
                fillFunction = {
                    KPN.getPortsList(numberOfItemsToGenerate)
                }
            }
            Carriers.LycaMobileNL-> {
                fillFunction = {
                    List(numberOfItemsToGenerate) {
                        (2048..65535).random()
                    }
                }
            }
            Carriers.Test -> {
                val l = List(50000) {
                    (2048..65535).random()
                }.toMutableList() + List(1) {9999}.toMutableList()
                fillFunction = {l}
            }
//            Carriers.Odido-> {}
//            Carriers.OrangeFR -> {}
//            Carriers.SFR -> {}
//            Carriers.OrangeBG -> {}
//            Carriers.LycaMobileBG -> {}
//            Carriers.TeliaNO -> {}
//            Carriers.MyCallNO -> {}
            else -> { // Note the block
                fillFunction = {
                    List(numberOfItemsToGenerate) {
                        (1024..65535).random()
                    }
                }
            }

        }
        queue = ObservableQueue(threshold, fillFunction)
        queue.fillQueue()
        queue.addListener(object : ObservableQueue.QueueListener {
            override fun onQueueBelowThreshold() {
                queue.fillQueue()
            }
        })
    }

    fun getNextPort(): Int {
        return queue.poll()!!
    }



}

suspend fun main() {
    val portChooser = PortChooser(Carriers.KPN)
    portChooser.setup()
    for (i in 0..50000) {
        delay(5)
        println("index  $i, element ${portChooser.getNextPort()}")
    }

}
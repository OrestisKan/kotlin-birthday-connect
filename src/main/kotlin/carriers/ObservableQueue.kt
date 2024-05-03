package carriers

import java.util.LinkedList

class ObservableQueue<T>(private val threshold: Int, private val fillFunction: () -> List<T>) {
    private val queue: LinkedList<T> = LinkedList()
    private val listeners: MutableList<QueueListener> = mutableListOf()

    fun addListener(listener: QueueListener) {
        listeners.add(listener)
    }

    fun poll(): T? {
        val element = queue.poll()
        if (queue.size < threshold) {
            listeners.forEach { it.onQueueBelowThreshold() }
        }
        return element
    }

    fun fillQueue() {
        fillFunction.invoke().forEach { queue.offer(it) }
    }

    fun size(): Int = queue.size

    interface QueueListener {
        fun onQueueBelowThreshold()
    }
}
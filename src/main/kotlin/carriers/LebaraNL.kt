package carriers

class LebaraNL {
    companion object {
        fun getPortsList(numberOfItemsToGenerate: Int): List<Int> {
            val start = 1024
            val end = 65535
            val divisibleBy256List = (start..end).filter { it % 256 == 0 }
            val allScalars = (0..255)
            val res = mutableListOf<Int>()
            var count = 0
            while (count < numberOfItemsToGenerate) {
                val randomNum = divisibleBy256List.random()
                res.add(randomNum)
                count++
                val scalars = allScalars.asSequence()
                    .shuffled()
                    .take(60)
                    .toList()
                for (num in scalars) {
                    res.add(randomNum + num)
                    count++
                }
            }
            return res.toList()
        }
    }
}
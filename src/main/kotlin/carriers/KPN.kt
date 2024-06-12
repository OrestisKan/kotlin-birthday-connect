package carriers

import java.util.Random

class KPN {
    companion object {
        fun getPortsList(numberOfItemsToGenerate: Int): List<Int> {
            val start = 1024
            val end = 65535
            val divisibleBy256List = (start..end).filter { it % 256 == 0 }
            val allScalars = (0..255)
            val res = mutableListOf<Int>()
            var count = 0
            res.addAll(divisibleBy256List.shuffled())
            res.addAll(divisibleBy256List.shuffled())
            count += divisibleBy256List.size * 2

            while(count < numberOfItemsToGenerate*2){
                val randomNum = divisibleBy256List.random()
                val scalars =  allScalars.asSequence().shuffled().take(100).toList()
                for(num in scalars){
                    res.add(randomNum + num)
                    count++
                }
            }
            res.addAll(divisibleBy256List.shuffled())
            return res.toList()
        }
    }
}
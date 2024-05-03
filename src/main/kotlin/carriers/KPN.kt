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
            while(count < numberOfItemsToGenerate){
                val randomNum = divisibleBy256List.random()
                val scalars = if((0..10).random() >= 4)
                        allScalars.asSequence().shuffled().take(51).toList()
                    else
                        allScalars.filter {it % 5 ==0} // 36% chance to go for a full block
                for(num in scalars){
                    res.add(randomNum + num)
                    count++
                }
            }
            return res.toList()
        }
    }
}
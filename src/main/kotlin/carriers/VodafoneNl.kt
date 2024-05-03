package carriers
import org.apache.commons.math3.distribution.BetaDistribution
class VodafoneNl {

    companion object { //todo not sure if correct!
        private const val ALPHA = 2.241976448936059
        private const val BETA = 5.0077200261695065
        private const val LOCATION = 4630.720419189701
        private const val SCALE = 13937.381826949593
//        private const val adjustedAlpha = (ALPHA - LOCATION) / SCALE
//        private const val adjustedBeta = (BETA - LOCATION) / SCALE
        private val betaDistribution = BetaDistribution(ALPHA, BETA)
        fun sampleBeta(): Int {
            return (LOCATION + SCALE * betaDistribution.sample()).toInt()
        }
    }
}
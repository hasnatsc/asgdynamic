package com.asg.fabricerp.global.documents;

/**
 * How greige is woven and stocked. CONSTRUCTION: one greige quality per production-order fabric
 * line, dyed into its colours afterwards (greige and piece-dyed routes). COLOUR: the colour is in
 * the yarn, so weaving and greige stock stay per colour line (yarn-dyed and denim routes).
 */
public enum GreigeKey { CONSTRUCTION, COLOUR }

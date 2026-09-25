package com.asg.fabricerp.global.numbering;

/**
 * Whose counter a number is drawn from. {@code ORGANIZATION}: one sequence for the whole company,
 * whichever unit raises the document. {@code BRANCH}: each business unit counts on its own, which
 * is only unique if the unit code is part of the number - so a branch-scoped pattern must carry
 * {@code {BRANCH}}.
 */
public enum CounterScope {
    ORGANIZATION,
    BRANCH
}

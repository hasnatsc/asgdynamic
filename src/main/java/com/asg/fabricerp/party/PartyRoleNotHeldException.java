package com.asg.fabricerp.party;

import java.util.Set;

/**
 * A party named on a document it is not permitted to appear on.
 *
 * <p>The message names the roles the party does hold, because the usual cause is that somebody
 * picked the right company from the wrong dropdown - and knowing it is a supplier rather than a
 * customer tells them which screen to go to. An {@link IllegalArgumentException}, so the API
 * answers 400 with this sentence.
 */
public class PartyRoleNotHeldException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public PartyRoleNotHeldException(String partyCode, PartyRoleType required, Set<PartyRoleType> held) {
        super("Party " + partyCode + " does not hold role " + required + "; it holds "
            + (held.isEmpty() ? "no roles at all" : String.join(", ", held.stream().map(Enum::name).sorted().toList())));
    }
}

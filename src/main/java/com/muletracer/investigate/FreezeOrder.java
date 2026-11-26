package com.muletracer.investigate;

import java.util.List;

/**
 * One recommended action sent to a bank's fraud desk.
 *
 * @param accountId   account to act on
 * @param bank        bank holding the account
 * @param city        branch city
 * @param hop         layer distance from the victim
 * @param heldPaise   victim money a debit-freeze recovers right now
 * @param score       mule risk score 0-100
 * @param verdict     LIKELY_MULE / REVIEW / LIKELY_INNOCENT
 * @param action      FREEZE (debit freeze), LIEN (mark lien on the held amount only) or BLOCK (chokepoint, block outward debits)
 * @param chokepoint  true if this account is in the minimum vertex cut
 * @param ringId      mule ring id, or null
 * @param reasons     evidence for the request
 */
public record FreezeOrder(String accountId, String bank, String city, int hop, long heldPaise, int score,
                          String verdict, String action, boolean chokepoint, String ringId,
                          List<String> reasons) {
}

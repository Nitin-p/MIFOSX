/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.portfolio.financialyear.exception;

import org.mifosplatform.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class FinancialYearCannotBeUpdatedException extends AbstractPlatformDomainRuleException {

    /*** enum of reasons of why Loan Charge cannot be waived **/
    public static enum FINANCIALYEAR_CANNOT_BE_UPDATED_REASON {
        LOAN_NOT_IN_SUBMITTED_AND_PENDING_APPROVAL_STAGE, FINANCIALYEAR_CLOSED;

        public String errorMessage() {
            if (name().toString().equalsIgnoreCase("LOAN_NOT_IN_SUBMITTED_AND_PENDING_APPROVAL_STAGE")) {
                return "This collateral cannot be updated as the loan it is associated with is not in submitted and pending approval stage";
            } else if (name().toString().equalsIgnoreCase("FINANCIALYEAR_CLOSED")) {
                return "Financial year closed";
            }
            return name().toString();
        }

        public String errorCode() {
            if (name().toString().equalsIgnoreCase("LOAN_NOT_IN_SUBMITTED_AND_PENDING_APPROVAL_STAGE")) {
                return "error.msg.loan.collateral.associated.loan.not.in.submitted.and.pending.approval.stage";
            } else if (name().toString().equalsIgnoreCase("FINANCIALYEAR_CLOSED")) {
                return "error.msg.financialyear.closed";
            }
            return name().toString();
        }
    }

    public FinancialYearCannotBeUpdatedException(final FINANCIALYEAR_CANNOT_BE_UPDATED_REASON reason, final Long resourceId) {
        super(reason.errorCode(), reason.errorMessage(), resourceId);
    }
}

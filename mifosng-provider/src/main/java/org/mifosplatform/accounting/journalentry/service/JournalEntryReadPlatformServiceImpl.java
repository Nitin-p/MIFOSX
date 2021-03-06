/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.accounting.journalentry.service;

import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.mifosplatform.accounting.common.AccountingEnumerations;
import org.mifosplatform.accounting.journalentry.data.JournalEntryAssignment;
import org.mifosplatform.accounting.journalentry.data.JournalEntryAssociationParametersData;
import org.mifosplatform.accounting.journalentry.data.JournalEntryData;
import org.mifosplatform.accounting.journalentry.data.TransactionDetailData;
import org.mifosplatform.accounting.journalentry.data.TransactionTypeEnumData;
import org.mifosplatform.accounting.journalentry.exception.JournalEntriesNotFoundException;
import org.mifosplatform.infrastructure.codes.data.CodeValueData;
import org.mifosplatform.infrastructure.core.data.EnumOptionData;
import org.mifosplatform.infrastructure.core.domain.JdbcSupport;
import org.mifosplatform.infrastructure.core.service.Page;
import org.mifosplatform.infrastructure.core.service.PaginationHelper;
import org.mifosplatform.infrastructure.core.service.RoutingDataSource;
import org.mifosplatform.infrastructure.core.service.SearchParameters;
import org.mifosplatform.organisation.monetary.data.CurrencyData;
import org.mifosplatform.portfolio.account.PortfolioAccountType;
import org.mifosplatform.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.mifosplatform.portfolio.loanproduct.service.LoanEnumerations;
import org.mifosplatform.portfolio.note.data.NoteData;
import org.mifosplatform.portfolio.paymentdetail.data.PaymentDetailData;
import org.mifosplatform.portfolio.savings.data.SavingsAccountTransactionEnumData;
import org.mifosplatform.portfolio.savings.service.SavingsEnumerations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

@Service
public class JournalEntryReadPlatformServiceImpl implements JournalEntryReadPlatformService {

	private final JdbcTemplate jdbcTemplate;

	private final PaginationHelper<JournalEntryData> paginationHelper = new PaginationHelper<>();

	@Autowired
	public JournalEntryReadPlatformServiceImpl(final RoutingDataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	private static final class GLJournalEntryMapper implements RowMapper<JournalEntryData> {

		private final JournalEntryAssociationParametersData associationParametersData;

		public GLJournalEntryMapper(final JournalEntryAssociationParametersData associationParametersData) {
			if (associationParametersData == null) {
				this.associationParametersData = new JournalEntryAssociationParametersData();
			} else {
				this.associationParametersData = associationParametersData;
			}
		}

		public String schema() {
			StringBuilder sb = new StringBuilder();
			sb.append(" journalEntry.id as id, glAccount.classification_enum as classification ,")
					.append("journalEntry.transaction_id,")
					.append(" glAccount.name as glAccountName, glAccount.currency_code as glAccountCurrencyCode, glAccount.gl_code as glAccountCode, glAccount.id as glAccountId, ")
					.append(" journalEntry.office_id as officeId, office.name as officeName, journalEntry.ref_num as referenceNumber, ")
					.append(" journalEntry.manual_entry as manualEntry,journalEntry.entry_date as transactionDate, ")
					.append(" journalEntry.unidentified_entry as unidentifiedEntry, ")
					.append(" journalEntry.type_enum as entryType,journalEntry.amount as amount,journalEntry.exchange_rate as exchangeRate, journalEntry.transaction_id as transactionId,")
					.append(" journalEntry.entity_type_enum as entityType, journalEntry.entity_id as entityId, creatingUser.id as createdByUserId, ")
					.append(" creatingUser.username as createdByUserName, journalEntry.description as comments, ")
					.append(" journalEntry.created_date as createdDate, journalEntry.reversed as reversed, ")
					.append(" journalEntry.currency_code as currencyCode, curr.name as currencyName, curr.internationalized_name_code as currencyNameCode, ")
					.append(" curr.display_symbol as currencyDisplaySymbol, curr.decimal_places as currencyDigits, curr.currency_multiplesof as inMultiplesOf, ")
					.append(" journalEntry.profit as isProfit, journalEntry.profit_transaction_id as profitTransactionId, (ltex.id is not null) as usedInLoan, ")
					.append(" (reversalJournalEntry.id is not null) as isReversalEntry, ")
					.append(" lt.is_reversed as isTransactionReversed ");
			if (associationParametersData.isRunningBalanceRequired()) {
				sb.append(" ,journalEntry.is_running_balance_calculated as runningBalanceComputed, ")
						.append(" journalEntry.office_running_balance as officeRunningBalance, ")
						.append(" journalEntry.organization_running_balance as organizationRunningBalance ");
			}
			if (associationParametersData.isTransactionDetailsRequired()) {
				sb.append(" ,pd.receipt_number as receiptNumber, ").append(" pd.check_number as checkNumber, ")
						.append(" pd.account_number as accountNumber, ").append(" cdv.code_value as paymentTypeName, ")
						.append(" pd.payment_type_cv_id as paymentTypeId,").append(" pd.bank_number as bankNumber, ")
						.append(" pd.routing_code as routingCode, ").append(" note.id as noteId, ")
						.append(" note.note as transactionNote, ").append(" lt.transaction_type_enum as loanTransactionType, ")
						.append(" st.transaction_type_enum as savingsTransactionType ");
			}
			sb.append(" from acc_gl_journal_entry as journalEntry ")
					.append(" left join acc_gl_account as glAccount on glAccount.id = journalEntry.account_id")
					.append(" left join m_office as office on office.id = journalEntry.office_id")
					.append(" left join m_appuser as creatingUser on creatingUser.id = journalEntry.createdby_id ")
					.append(" join m_currency curr on curr.code = journalEntry.currency_code ");
			if (associationParametersData.isTransactionDetailsRequired()) {
				sb.append(" left join m_savings_account_transaction as st on journalEntry.savings_transaction_id = st.id ")
						.append(" left join m_payment_detail as pd on lt.payment_detail_id = pd.id or st.payment_detail_id = pd.id")
						.append(" left join m_code_value as cdv on cdv.id = pd.payment_type_cv_id ")
						.append(" left join m_note as note on lt.id = note.loan_transaction_id or st.id = note.savings_account_transaction_id ");
			}
			sb.append(" left join m_loan_transaction as ltex on journalEntry.transaction_id = ltex.related_transaction_id ");
			sb.append(" left join acc_gl_journal_entry as reversalJournalEntry on journalEntry.id = reversalJournalEntry.reversal_id ");
			sb.append(" left join m_loan_transaction as lt on journalEntry.loan_transaction_id = lt.id ");
			return sb.toString();

		}

		@Override
		public JournalEntryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {

			final Long id = rs.getLong("id");
			final Long officeId = rs.getLong("officeId");
			final String officeName = rs.getString("officeName");
			final String glCode = rs.getString("glAccountCode");
			final String glAccountName = rs.getString("glAccountName");
			final Long glAccountId = rs.getLong("glAccountId");
			final int accountTypeId = JdbcSupport.getInteger(rs, "classification");
			final EnumOptionData accountType = AccountingEnumerations.gLAccountType(accountTypeId);
			final LocalDate transactionDate = JdbcSupport.getLocalDate(rs, "transactionDate");
			final Boolean manualEntry = rs.getBoolean("manualEntry");
			final BigDecimal amount = rs.getBigDecimal("amount");
			final BigDecimal exchangeRate = rs.getBigDecimal("exchangeRate");
			final int entryTypeId = JdbcSupport.getInteger(rs, "entryType");
			final EnumOptionData entryType = AccountingEnumerations.journalEntryType(entryTypeId);
			final String transactionId = rs.getString("transactionId");
			final Integer entityTypeId = JdbcSupport.getInteger(rs, "entityType");
			EnumOptionData entityType = null;
			if (entityTypeId != null) {
				entityType = AccountingEnumerations.portfolioProductType(entityTypeId);

			}

			final Long entityId = JdbcSupport.getLong(rs, "entityId");
			final Long createdByUserId = rs.getLong("createdByUserId");
			final LocalDate createdDate = JdbcSupport.getLocalDate(rs, "createdDate");
			final String createdByUserName = rs.getString("createdByUserName");
			final String comments = rs.getString("comments");
			final Boolean reversed = rs.getBoolean("reversed");
			final String referenceNumber = rs.getString("referenceNumber");
			BigDecimal officeRunningBalance = null;
			BigDecimal organizationRunningBalance = null;
			Boolean runningBalanceComputed = null;

			final String currencyCode = rs.getString("currencyCode");
			final String currencyName = rs.getString("currencyName");
			final String currencyNameCode = rs.getString("currencyNameCode");
			final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
			final Integer currencyDigits = JdbcSupport.getInteger(rs, "currencyDigits");
			final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");
			final CurrencyData currency = new CurrencyData(currencyCode, currencyName, currencyDigits, inMultiplesOf,
					currencyDisplaySymbol, currencyNameCode);
			final Boolean unidentifiedEntry = rs.getBoolean("unidentifiedEntry");
			final Boolean isProfit = rs.getBoolean("isProfit");
			final String profitTransactionId = rs.getString("profitTransactionId");
			final Boolean usedInLoan = rs.getBoolean("usedInLoan");
			final Boolean isReversalEntry = rs.getBoolean("isReversalEntry");
			final Boolean isTransactionReversed = rs.getBoolean("isTransactionReversed");

			if (associationParametersData.isRunningBalanceRequired()) {
				officeRunningBalance = rs.getBigDecimal("officeRunningBalance");
				organizationRunningBalance = rs.getBigDecimal("organizationRunningBalance");
				runningBalanceComputed = rs.getBoolean("runningBalanceComputed");
			}
			TransactionDetailData transactionDetailData = null;

			if (associationParametersData.isTransactionDetailsRequired()) {
				PaymentDetailData paymentDetailData = null;
				final Long paymentTypeId = JdbcSupport.getLong(rs, "paymentTypeId");
				if (paymentTypeId != null) {
					final String typeName = rs.getString("paymentTypeName");
					final CodeValueData paymentType = CodeValueData.instance(paymentTypeId, typeName);
					final String accountNumber = rs.getString("accountNumber");
					final String checkNumber = rs.getString("checkNumber");
					final String routingCode = rs.getString("routingCode");
					final String receiptNumber = rs.getString("receiptNumber");
					final String bankNumber = rs.getString("bankNumber");
					paymentDetailData = new PaymentDetailData(id, paymentType, accountNumber, checkNumber, routingCode, receiptNumber,
							bankNumber);
				}
				NoteData noteData = null;
				final Long noteId = JdbcSupport.getLong(rs, "noteId");
				if (noteId != null) {
					final String note = rs.getString("transactionNote");
					noteData = new NoteData(noteId, null, null, null, null, null, null, null, note, null, null, null, null, null, null);
				}
				Long transaction = null;
				if (entityType != null) {
					transaction = Long.parseLong(transactionId.substring(1).trim());
				}

				TransactionTypeEnumData transactionTypeEnumData = null;

				if (PortfolioAccountType.fromInt(entityTypeId).isLoanAccount()) {
					final LoanTransactionEnumData loanTransactionType = LoanEnumerations.transactionType(JdbcSupport.getInteger(rs,
							"loanTransactionType"));
					transactionTypeEnumData = new TransactionTypeEnumData(loanTransactionType.id(), loanTransactionType.getCode(),
							loanTransactionType.getValue());
				} else if (PortfolioAccountType.fromInt(entityTypeId).isSavingsAccount()) {
					final SavingsAccountTransactionEnumData savingsTransactionType = SavingsEnumerations.transactionType(JdbcSupport
							.getInteger(rs, "savingsTransactionType"));
					transactionTypeEnumData = new TransactionTypeEnumData(savingsTransactionType.getId(), savingsTransactionType.getCode(),
							savingsTransactionType.getValue());
				}

				transactionDetailData = new TransactionDetailData(transaction, paymentDetailData, noteData, transactionTypeEnumData);
			}
			return new JournalEntryData(id, officeId, officeName, glAccountName, glAccountId, glCode, accountType, transactionDate,
					entryType, amount, exchangeRate, transactionId, manualEntry, entityType, entityId, createdByUserId, createdDate, createdByUserName,
					comments, reversed, referenceNumber, officeRunningBalance, organizationRunningBalance, runningBalanceComputed,
					transactionDetailData, currency, unidentifiedEntry, isProfit, profitTransactionId, usedInLoan, isReversalEntry, isTransactionReversed);
		}
	}

	@Override
	public Page<JournalEntryData> retrieveAll(final SearchParameters searchParameters, final Long glAccountId,
	                                          final Boolean onlyManualEntries, final Date fromDate, final Date toDate, final String transactionId, final Integer entityType,
	                                          final JournalEntryAssociationParametersData associationParametersData, final Boolean onlyUnidentifiedEntries) {

		GLJournalEntryMapper rm = new GLJournalEntryMapper(associationParametersData);
		final StringBuilder sqlBuilder = new StringBuilder(200);
		sqlBuilder.append("select SQL_CALC_FOUND_ROWS ");
		sqlBuilder.append(rm.schema());

		final Object[] objectArray = new Object[5];
		int arrayPos = 0;
		String whereClose = " where ";

		if (StringUtils.isNotBlank(transactionId)) {
			sqlBuilder.append(whereClose + " journalEntry.transaction_id = ?");
			objectArray[arrayPos] = transactionId;
			arrayPos = arrayPos + 1;

			whereClose = " and ";
		}

		if (entityType != null && entityType != 0 && (onlyManualEntries == null)) {

			sqlBuilder.append(whereClose + " journalEntry.entity_type_enum = ?");

			objectArray[arrayPos] = entityType;
			arrayPos = arrayPos + 1;

			whereClose = " and ";
		}

		if (searchParameters.isOfficeIdPassed()) {
			sqlBuilder.append(whereClose + " journalEntry.office_id = ?");
			objectArray[arrayPos] = searchParameters.getOfficeId();
			arrayPos = arrayPos + 1;

			whereClose = " and ";
		}

		if (glAccountId != null && glAccountId != 0) {
			sqlBuilder.append(whereClose + " journalEntry.account_id = ?");
			objectArray[arrayPos] = glAccountId;
			arrayPos = arrayPos + 1;

			whereClose = " and ";
		}

		if (fromDate != null || toDate != null) {
			final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
			String fromDateString = null;
			String toDateString = null;
			if (fromDate != null && toDate != null) {
				sqlBuilder.append(whereClose + " journalEntry.entry_date between ? and ? ");

				whereClose = " and ";

				fromDateString = df.format(fromDate);
				toDateString = df.format(toDate);
				objectArray[arrayPos] = fromDateString;
				arrayPos = arrayPos + 1;
				objectArray[arrayPos] = toDateString;
				arrayPos = arrayPos + 1;
			} else if (fromDate != null) {
				sqlBuilder.append(whereClose + " journalEntry.entry_date >= ? ");
				fromDateString = df.format(fromDate);
				objectArray[arrayPos] = fromDateString;
				arrayPos = arrayPos + 1;
				whereClose = " and ";

			} else if (toDate != null) {
				sqlBuilder.append(whereClose + " journalEntry.entry_date <= ? ");
				toDateString = df.format(toDate);
				objectArray[arrayPos] = toDateString;
				arrayPos = arrayPos + 1;

				whereClose = " and ";
			}
		}

		if (onlyManualEntries != null) {
			if (onlyManualEntries) {
				sqlBuilder.append(whereClose + " journalEntry.manual_entry = 1");

				whereClose = " and ";
			}
		}

		if (onlyUnidentifiedEntries != null) {
			if (onlyUnidentifiedEntries) {
				sqlBuilder.append(whereClose + " journalEntry.unidentified_entry = 1 and ltex.id is null ");

				whereClose = " and ";
			}
		}

		if (searchParameters.isOrderByRequested()) {
			sqlBuilder.append(" order by ").append(searchParameters.getOrderBy());

			if (searchParameters.isSortOrderProvided()) {
				sqlBuilder.append(' ').append(searchParameters.getSortOrder());
			}
		} else {
			sqlBuilder.append(" order by journalEntry.entry_date, journalEntry.id");
		}

		if (searchParameters.isLimited()) {
			sqlBuilder.append(" limit ").append(searchParameters.getLimit());
			if (searchParameters.isOffset()) {
				sqlBuilder.append(" offset ").append(searchParameters.getOffset());
			}
		}

		final Object[] finalObjectArray = Arrays.copyOf(objectArray, arrayPos);
		final String sqlCountRows = "SELECT FOUND_ROWS()";
		return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlCountRows, sqlBuilder.toString(), finalObjectArray, rm);
	}

	@Override
	public Collection<JournalEntryAssignment> retrieveJournalEntryAssignments(Long journalEntryId) {
		JournalEntryAssignmentMapper journalEntryAssignmentMapper = new JournalEntryAssignmentMapper();
		return this.jdbcTemplate.query(journalEntryAssignmentMapper.sql(), new Object[]{journalEntryId}, journalEntryAssignmentMapper);
	}

	private static final class JournalEntryAssignmentMapper implements RowMapper<JournalEntryAssignment> {

		public String sql() {
			return new StringBuilder("SELECT j.id journal_id, l.id loan_id, c.display_name, e.enum_value, c.external_id, l.account_no ")
					.append("FROM acc_gl_journal_entry j ")
					.append("INNER JOIN m_loan_transaction lt ON lt.related_transaction_id = j.transaction_id ")
					.append("INNER JOIN m_loan l ON l.id = lt.loan_id ")
					.append("INNER JOIN m_client c on c.id = l.client_id ")
					.append("INNER JOIN r_enum_value e ON e.enum_id = l.loan_status_id ")
					.append("WHERE j.id = ? ")
					.append("AND lt.is_reversed = 0")
					.toString();
		}

		;

		@Override
		public JournalEntryAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
			JournalEntryAssignment journalEntryAssignment = new JournalEntryAssignment();
			journalEntryAssignment.setLoanId(rs.getLong("loan_id"));
			journalEntryAssignment.setJournalId(rs.getLong("journal_id"));
			journalEntryAssignment.setLoanStatus(rs.getString("enum_value"));
			journalEntryAssignment.setClientName(rs.getString("display_name"));
			journalEntryAssignment.setClientFileNumber(rs.getString("external_id"));
			journalEntryAssignment.setLoanAccountNumber(rs.getString("account_no"));
			return journalEntryAssignment;
		}
	}

	@Override
	public JournalEntryData retrieveGLJournalEntryById(final long glJournalEntryId,
	                                                   JournalEntryAssociationParametersData associationParametersData) {
		try {

			final GLJournalEntryMapper rm = new GLJournalEntryMapper(associationParametersData);
			final String sql = "select " + rm.schema() + " where journalEntry.id = ?";

			final JournalEntryData glJournalEntryData = this.jdbcTemplate.queryForObject(sql, rm, new Object[]{glJournalEntryId});

			return glJournalEntryData;
		} catch (final EmptyResultDataAccessException e) {
			throw new JournalEntriesNotFoundException(glJournalEntryId);
		}
	}

	@Override
	public Long getJournalEntriesCount(final String filter, final String search) {
		JournalEntryCountMapper mapper = new JournalEntryCountMapper();
		return this.jdbcTemplate.queryForObject(mapper.sql(), mapper, filter, filter, filter,
				search, search, search, search, search, search);
	}

	private static final class JournalEntryCountMapper implements RowMapper<Long> {

		public String sql() {
			return new StringBuilder("SELECT COUNT(tt.id) AS je_count FROM (")
					.append("SELECT y.id FROM (")
					.append("SELECT m.id, DATE_FORMAT(m.created_date,'%d/%m/%Y') createdOn, DATE_FORMAT(m.entry_date,'%d/%m/%Y') transactionDate, ")
					.append("c.display_name clientName, description, entry_date, ")
					.append("SUM(case when type_enum=1 then if(lt.is_reversed, m.amount / 2, m.amount) ELSE 0 END) as 'Credit', ")
					.append("SUM(case when type_enum=2 then if(lt.is_reversed, m.amount / 2, m.amount) ELSE 0 END) as 'Debit' ")
					.append("FROM acc_gl_journal_entry m ")
					.append("LEFT JOIN m_office o ON o.id=m.office_id ")
					.append("LEFT JOIN m_loan l ON l.id=m.entity_id ")
					.append("LEFT JOIN m_loan_transaction lt ON lt.id = m.loan_transaction_id ")
					.append("LEFT JOIN m_client c ON c.id=l.client_id ")
					.append("WHERE 1 ")
					.append("AND IF(? = 'reversed', m.reversed or lt.is_reversed, 1) ")
					.append("AND IF(? = 'unidentified_profit', m.profit, 1) ")
					.append("AND IF(? = 'unidentified_deposits', m.unidentified_entry, 1) ")
					.append("GROUP BY transaction_id) y ")
					.append("WHERE 1 AND (")
					.append("y.description LIKE ? ")
					.append("OR y.clientName LIKE ? ")
					.append("OR y.createdOn LIKE ? ")
					.append("OR y.transactionDate LIKE ? ")
					.append("OR convert(y.Credit, char) LIKE ? ")
					.append("OR convert(y.Debit, char) LIKE ?)) tt ")
					.toString();
		}

		@Override
		public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
			return rs.getLong("je_count");
		}
	}

}

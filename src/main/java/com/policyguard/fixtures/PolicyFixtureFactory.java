package com.policyguard.fixtures;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Programmatically builds all 8 synthetic policy PDF fixtures using PDFBox 3.
 *
 * <p>Each PDF:
 * <ul>
 *   <li>Spans at least 2 pages so PDFBox extraction is exercised end-to-end.</li>
 *   <li>Uses clearly numbered sections ({@code 4.1 Retention Periods}) so
 *       {@code ChunkingService} produces meaningful {@code paragraph_ref} values.</li>
 *   <li>Contains obviously fake PII (names {@code *-TEST} / {@code *-FAKE},
 *       emails {@code @company-test.example}, SSNs {@code 000-00-0000},
 *       phones {@code (555) 000-000x}).</li>
 *   <li>High-risk documents contain GDPR/HIPAA/PCI-DSS references and
 *       exception/waiver language that the configured risk-classifier patterns
 *       will flag on relevant queries.</li>
 * </ul>
 */
public class PolicyFixtureFactory {

    // ── public API ─────────────────────────────────────────────────────────────

    /**
     * Immutable value object returned for each synthetic document.
     *
     * @param documentId human-readable stable ID, e.g. {@code POL-TEST-001}
     * @param docType    one of {@code policy | sop | control | faq}
     * @param title      human-readable document title
     * @param pdfBytes   raw PDF bytes (multi-page)
     * @param metadata   additional key/value metadata (piiDensity, highRisk, …)
     */
    public record PolicyFixture(
            String documentId,
            String docType,
            String title,
            byte[] pdfBytes,
            Map<String, Object> metadata) {}

    /** Builds all 8 fixtures in spec-table order. */
    public List<PolicyFixture> buildAll() {
        return List.of(
                buildPol001(),
                buildSop002(),
                buildCtl003(),
                buildFaq004(),
                buildPol005(),
                buildSop006(),
                buildPol007(),
                buildFaq008());
    }

    /**
     * Builds a single fixture by its stable document ID.
     *
     * @throws IllegalArgumentException if {@code documentId} is unknown
     */
    public PolicyFixture build(String documentId) {
        return switch (documentId) {
            case "POL-TEST-001" -> buildPol001();
            case "SOP-TEST-002" -> buildSop002();
            case "CTL-TEST-003" -> buildCtl003();
            case "FAQ-TEST-004" -> buildFaq004();
            case "POL-TEST-005" -> buildPol005();
            case "SOP-TEST-006" -> buildSop006();
            case "POL-TEST-007" -> buildPol007();
            case "FAQ-TEST-008" -> buildFaq008();
            default -> throw new IllegalArgumentException("Unknown fixture: " + documentId);
        };
    }

    // ── individual document builders ───────────────────────────────────────────

    private PolicyFixture buildPol001() {
        String title = "Data Retention and Privacy Policy";
        List<Section> sections = List.of(
                new Section("1 Overview", List.of(
                        "This policy governs the collection, retention, protection, and disposal of personal " +
                        "information processed by the organization across all business units and systems.",
                        "All employees, contractors, and third-party service providers who handle personal " +
                        "data on behalf of the organization are bound by this policy."
                )),
                new Section("1.1 Purpose", List.of(
                        "This policy establishes the requirements for the retention, protection, and disposal " +
                        "of personal information collected by the organization in the course of its operations.",
                        "This policy applies to all personal data processed by the organization, whether " +
                        "stored electronically or maintained in physical form, regardless of storage medium."
                )),
                new Section("2.1 Applicability", List.of(
                        "This policy applies to all employees, contractors, and third-party service providers " +
                        "who process personal data on behalf of the organization in any capacity.",
                        "Data subjects whose information is processed by the organization retain rights under " +
                        "applicable data protection laws, including the right of access and erasure."
                )),
                new Section("3 Definitions", List.of(
                        "Customer PII: Any personally identifiable information of customers, including but " +
                        "not limited to full names, email addresses, social security numbers (SSN format " +
                        "000-00-0000), telephone numbers such as (555) 000-0001, and account identifiers.",
                        "Data Subject: The individual to whom personal data relates, as defined under " +
                        "applicable data protection regulations including GDPR where applicable.",
                        "Retention Period: The defined duration for which personal data must be kept before " +
                        "secure disposal, established by legal, regulatory, or business requirements."
                )),
                new Section("4 Data Retention Requirements", List.of(
                        "Data retention periods are established based on legal requirements, regulatory " +
                        "obligations, and business needs and must be adhered to by all data custodians.",
                        "The retention schedule is reviewed annually by the Compliance Officer and updated " +
                        "to reflect changes in applicable law or organizational requirements."
                )),
                new Section("4.1 Retention Periods", List.of(
                        "Different categories of personal data are subject to different minimum retention " +
                        "periods as specified in the Data Retention Schedule appended to this policy.",
                        "Customer PII shall be retained for a period of seven (7) years following account " +
                        "closure, in compliance with applicable financial record-keeping regulations.",
                        "Employee records shall be retained for a minimum of five (5) years following the " +
                        "termination of employment or the end of the contractual relationship."
                )),
                new Section("4.2 Disposal Procedures", List.of(
                        "Upon expiration of the applicable retention period, personal data must be securely " +
                        "disposed of in a manner that prevents unauthorized recovery or reconstruction.",
                        "Electronic records shall be permanently destroyed using overwriting techniques " +
                        "conforming to DoD 5220.22-M standard, or cryptographic erasure where applicable, " +
                        "with disposal documented in the Data Disposal Register.",
                        "Physical records containing PII must be cross-cut shredded and disposed of via " +
                        "certified secure destruction services. Proof of destruction must be retained for " +
                        "three (3) years."
                )),
                new Section("5.1 Compliance Officer", List.of(
                        "The Compliance Officer, currently Jane Doe-TEST (compliance@company-test.example, " +
                        "(555) 000-0001), is responsible for overseeing the implementation and enforcement " +
                        "of this policy across all business units.",
                        "All compliance concerns, policy questions, and data subject requests should be " +
                        "directed to compliance@company-test.example or escalated to the Legal department.",
                        "The Compliance Officer reports annually to the Board of Directors on data " +
                        "protection posture, policy exceptions granted, and remediation activities."
                )),
                new Section("6 Regulatory Compliance", List.of(
                        "This policy is designed to align with the requirements of GDPR, applicable state " +
                        "data protection laws, and industry-specific regulations where the organization " +
                        "operates as a data controller or processor.",
                        "Where GDPR compliance requirements apply to the processing of EU customer data, " +
                        "a Data Protection Impact Assessment (DPIA) must be conducted prior to any new " +
                        "high-risk processing activity.",
                        "Any exception to the data retention requirements defined in this policy must be " +
                        "documented and approved in writing by the Compliance Officer before implementation."
                ))
        );
        return fixture("POL-TEST-001", "policy", title, "medium", true, sections);
    }

    private PolicyFixture buildSop002() {
        String title = "Incident Response Procedure";
        List<Section> sections = List.of(
                new Section("1 Purpose", List.of(
                        "This Standard Operating Procedure (SOP) defines the structured process for " +
                        "detecting, reporting, classifying, containing, and recovering from information " +
                        "security incidents affecting the organization's systems and data.",
                        "All personnel with access to company systems have an obligation to report " +
                        "suspected security incidents promptly and to cooperate fully with investigations."
                )),
                new Section("2 Scope", List.of(
                        "This SOP applies to all information security incidents affecting company-owned " +
                        "or company-managed systems, networks, applications, and data repositories.",
                        "Third-party service providers are required by contract to adhere to equivalent " +
                        "incident response standards and to notify the organization within two (2) hours " +
                        "of detecting any incident involving organizational data."
                )),
                new Section("3 Incident Classification", List.of(
                        "Incidents are classified into four severity levels: Critical, High, Medium, and " +
                        "Low, based on the scope, nature, and potential impact of the event.",
                        "A Critical incident is defined as any breach involving unauthorized access to " +
                        "customer data or systems affecting more than one hundred (100) users, any " +
                        "ransomware infection, or any confirmed data exfiltration event.",
                        "A High incident includes unauthorized access limited to internal systems, " +
                        "targeted phishing attacks with credential compromise, or system outages " +
                        "affecting critical business operations."
                )),
                new Section("3.1 Severity Definitions", List.of(
                        "Critical severity incidents require immediate escalation and must be treated " +
                        "as the highest organizational priority, with all available resources mobilized.",
                        "High severity incidents require escalation within one (1) hour and must be " +
                        "investigated by the Incident Response Team within four (4) hours of detection.",
                        "Medium and Low severity incidents follow standard ticketing procedures " +
                        "with response times of eight (8) hours and twenty-four (24) hours respectively."
                )),
                new Section("4 Response Procedures", List.of(
                        "All incident response activities must be documented in the Incident Register " +
                        "and assigned a unique Incident ID (INC-YYYYMMDD-NNN format).",
                        "Response activities must be conducted by personnel with the appropriate role " +
                        "as defined in the Incident Response Team structure in Appendix A."
                )),
                new Section("4.1 Detection and Reporting", List.of(
                        "Upon detecting or suspecting a security incident, personnel must immediately " +
                        "notify the security team via incident-response@company-test.example or " +
                        "(555) 000-0002, without delay and before taking any remediation actions.",
                        "The initial report must be filed within one (1) hour of detection using the " +
                        "Incident Report Form IR-001, available on the IT Security intranet portal.",
                        "Reporters must preserve all evidence and logs, and must not power off affected " +
                        "systems or delete files prior to obtaining explicit authorization from the " +
                        "Incident Response Team Lead."
                )),
                new Section("4.2 Containment", List.of(
                        "Containment measures appropriate to the incident severity must be implemented " +
                        "within four (4) hours of incident classification by the Incident Response Team.",
                        "The Incident Response Team Lead, John Smith-FAKE (irteam@company-test.example, " +
                        "(555) 000-0007), must be notified immediately for all Critical incidents " +
                        "and within one (1) hour for High-severity incidents.",
                        "Network isolation, account suspension, and system quarantine measures may be " +
                        "invoked by the Team Lead without prior management approval for Critical " +
                        "incidents where immediate action is required to prevent further data loss."
                )),
                new Section("5.1 Escalation Paths", List.of(
                        "Critical incidents must be escalated to the Chief Information Security Officer " +
                        "(CISO) within thirty (30) minutes of initial classification and to the " +
                        "Chief Executive Officer within two (2) hours.",
                        "All escalations must be documented in the Incident Register and communicated " +
                        "via encrypted channel to incident-response@company-test.example.",
                        "Where a Critical incident may trigger GDPR breach notification obligations, " +
                        "the Data Protection Officer must be notified within one (1) hour to assess " +
                        "regulatory reporting requirements under applicable law."
                )),
                new Section("6 Post-Incident Review", List.of(
                        "A post-incident review must be conducted within five (5) business days of " +
                        "incident closure for all Critical and High severity incidents.",
                        "Lessons learned must be documented and used to update this SOP, security " +
                        "controls, and training programs to prevent recurrence.",
                        "All post-incident documentation is retained for a minimum of three (3) years " +
                        "in accordance with the Data Retention and Privacy Policy (POL-TEST-001)."
                ))
        );
        return fixture("SOP-TEST-002", "sop", title, "low", true, sections);
    }

    private PolicyFixture buildCtl003() {
        String title = "Access Control Matrix";
        List<Section> sections = List.of(
                new Section("1 Purpose", List.of(
                        "This document defines the access control framework for all company systems, " +
                        "applications, and data repositories, establishing role-based permissions and " +
                        "the processes for granting, modifying, and revoking access rights.",
                        "Access controls are a foundational security control and must be implemented " +
                        "consistently across all systems to protect organizational assets and customer data."
                )),
                new Section("2 Roles and Responsibilities", List.of(
                        "Access rights are assigned based on the principle of least privilege, ensuring " +
                        "personnel have only the minimum access required to perform their job functions.",
                        "The IT Security Manager is responsible for maintaining this matrix and " +
                        "conducting quarterly access reviews for all privileged accounts."
                )),
                new Section("2.1 Role Definitions", List.of(
                        "The System Administrator role is managed by John Smith-FAKE " +
                        "(sysadmin@company-test.example, EMP-ID: 00001, SSN: 000-00-0001), who is " +
                        "responsible for infrastructure provisioning and privileged account governance.",
                        "The Data Analyst role (contact: analyst@company-test.example) has read access " +
                        "to aggregate data sets; access to individual customer PII records requires " +
                        "explicit written authorization from the Data Protection Officer.",
                        "The Auditor role (contact: audit@company-test.example, EMP-ID: 00002, " +
                        "SSN: 000-00-0002) provides independent oversight with read-only access " +
                        "to all audit logs, system configurations, and compliance reports."
                )),
                new Section("3 Access Control Rules", List.of(
                        "All access to organizational systems requires unique individual accounts; " +
                        "shared or generic accounts are prohibited except for designated service accounts " +
                        "which must be documented in the Service Account Register.",
                        "Multi-factor authentication (MFA) is mandatory for all privileged accounts, " +
                        "remote access connections, and access to systems containing customer PII."
                )),
                new Section("3.1 System Administrator Access", List.of(
                        "System Administrators have full read, write, and execute access to all " +
                        "infrastructure systems, including servers, network devices, and cloud " +
                        "administration consoles, subject to the dual-authorization requirements below.",
                        "Access to production databases requires dual-authorization via MFA; no single " +
                        "administrator may modify production data without a second authorized reviewer " +
                        "confirming the change in the Change Management system.",
                        "All System Administrator actions in production environments are logged to an " +
                        "immutable audit trail (admin-audit@company-test.example) and reviewed weekly " +
                        "by the IT Security Manager."
                )),
                new Section("3.2 Standard User Access", List.of(
                        "Standard users have read access to shared document repositories and write " +
                        "access to their own work areas; they may not install software, modify system " +
                        "configuration, or access other users' files without explicit authorization.",
                        "Access to customer PII fields in operational systems requires explicit written " +
                        "manager approval documented in the Access Request Register, with justification " +
                        "aligned to the data minimization principle.",
                        "Standard user accounts for former employee Jane Doe-TEST (EMP-ID: 00003, " +
                        "SSN: 000-00-0003, email: j.doe@company-test.example, (555) 000-0003) " +
                        "must be revoked within one (1) business day of employment termination."
                )),
                new Section("4 Exception Process", List.of(
                        "Temporary or permanent exceptions to access control rules may be required " +
                        "for specific operational needs, but must never compromise the security " +
                        "posture of systems containing customer PII or financial data.",
                        "All exception requests must be reviewed by the IT Security Manager and " +
                        "assessed for potential security risk before any decision is made."
                )),
                new Section("4.1 Access Exception Requests", List.of(
                        "Exceptions to access controls must be submitted via the Access Exception " +
                        "Request Form (AER-001), available on the IT intranet, with full business " +
                        "justification, proposed duration, and risk acknowledgment from the requester.",
                        "All exceptions require written approval from the Security Committee and " +
                        "must not constitute an override of the policy control or a bypass of the " +
                        "requirement without documented risk acceptance signed by the CISO.",
                        "Approved exceptions are logged in the Exception Register and reviewed " +
                        "quarterly; any exception exceeding ninety (90) days must be re-evaluated " +
                        "and requires renewed Security Committee approval to remain active."
                )),
                new Section("5 Review Schedule", List.of(
                        "This access control matrix is reviewed and recertified annually by " +
                        "Compliance Officer Maria Rivera-TEST (maria.rivera@company-test.example, " +
                        "(555) 000-0004), with quarterly spot-checks for privileged accounts.",
                        "All role assignments are reviewed by the relevant department head semi-annually " +
                        "to ensure alignment with current job responsibilities and the principle of " +
                        "least privilege, with any anomalies reported to the IT Security Manager.",
                        "Access review findings are documented and remediation actions tracked to " +
                        "closure within thirty (30) days; unresolved findings are escalated to the CISO."
                ))
        );
        return fixture("CTL-TEST-003", "control", title, "high", true, sections);
    }

    private PolicyFixture buildFaq004() {
        String title = "Employee Benefits FAQ";
        List<Section> sections = List.of(
                new Section("1 Health Benefits", List.of(
                        "This section answers frequently asked questions about the health and wellness " +
                        "benefits available to eligible full-time and part-time employees.",
                        "For benefits enrolment assistance, contact HR Director Jane Doe-TEST at " +
                        "hr@company-test.example or (555) 000-0005 (Employee ID: EMP-00010, " +
                        "SSN: 000-00-0004)."
                )),
                new Section("1.1 Medical Insurance", List.of(
                        "Q: What health insurance options are available to employees? When does " +
                        "coverage begin for new hires joining the organization?",
                        "A: The company offers three tiers of medical coverage: Basic, Standard, and " +
                        "Premium. Basic covers essential services; Standard adds specialist and " +
                        "preventive care; Premium includes comprehensive dental and vision coverage. " +
                        "Coverage begins on the first day of the month following the hire date.",
                        "Q: Can employees add dependants to their health insurance plan, and what " +
                        "documentation is required to add a spouse or domestic partner?",
                        "A: Employees may add eligible dependants during open enrolment or within " +
                        "thirty (30) days of a qualifying life event. Documentation of relationship " +
                        "is required and should be submitted to hr@company-test.example."
                )),
                new Section("2 Retirement Benefits", List.of(
                        "Retirement benefit questions are handled by the Benefits Administration team " +
                        "at benefits@company-test.example or (555) 000-0006.",
                        "Employees are encouraged to review their retirement savings allocations " +
                        "annually and to attend the quarterly financial wellness workshops."
                )),
                new Section("2.1 Retirement Savings Plan", List.of(
                        "Q: What retirement savings plans are offered by the company to eligible " +
                        "employees, and when does the employer match contribution begin?",
                        "A: Employees are eligible to participate in the company 401(k) plan with " +
                        "a four percent (4%) employer match on contributions up to the IRS annual " +
                        "limit, effective after ninety (90) days of continuous employment.",
                        "Q: What happens to my 401(k) balance if I leave the company before the " +
                        "employer match vests?",
                        "A: The employer match vests on a three-year cliff schedule. Employees who " +
                        "leave before completing three years forfeit unvested employer contributions; " +
                        "employee contributions are always one hundred percent (100%) vested."
                )),
                new Section("3 Leave Policies", List.of(
                        "Leave policies are administered by the HR department; all leave requests " +
                        "must be submitted through the HR Self-Service portal at least five (5) " +
                        "business days in advance where operationally possible.",
                        "Emergency leave may be granted at manager discretion with HR notification " +
                        "within twenty-four (24) hours; documentation may be required retrospectively."
                )),
                new Section("3.1 Vacation Time", List.of(
                        "Q: How many vacation days are full-time employees entitled to per calendar " +
                        "year, and does unused vacation carry over to the following year?",
                        "A: Full-time employees receive fifteen (15) vacation days per calendar year, " +
                        "increasing to twenty (20) days after five (5) years of continuous service. " +
                        "Up to five (5) unused days may be carried over to the following year.",
                        "Q: How are vacation days calculated for employees who join mid-year or " +
                        "transition between full-time and part-time status during the year?",
                        "A: Vacation entitlement is prorated based on the hire date for the first " +
                        "year. Employees transitioning between employment classifications receive a " +
                        "prorated adjustment effective from the date of the classification change."
                )),
                new Section("4 Contact Information", List.of(
                        "For all benefits-related inquiries, please contact the HR Benefits team: " +
                        "Jane Doe-TEST, HR Director, hr@company-test.example, (555) 000-0005.",
                        "Employee records reference: Maria Rivera-TEST, Benefits Coordinator, " +
                        "benefits@company-test.example, EMP-00011, SSN: 000-00-0005, (555) 000-0006.",
                        "Benefits portal access issues should be reported to helpdesk@company-test.example " +
                        "with subject line 'Benefits Portal' for priority routing to the HR Systems team."
                ))
        );
        return fixture("FAQ-TEST-004", "faq", title, "high", false, sections);
    }

    private PolicyFixture buildPol005() {
        String title = "Acceptable Use Policy";
        List<Section> sections = List.of(
                new Section("1 Overview", List.of(
                        "This policy establishes the rules governing the acceptable use of company " +
                        "information technology resources by all employees, contractors, and authorized " +
                        "users, to protect the security and integrity of company systems and data.",
                        "Violation of this policy may result in disciplinary action up to and including " +
                        "termination of employment or contract, and potential legal proceedings."
                )),
                new Section("1.1 Policy Objectives", List.of(
                        "The primary objectives of this policy are to protect company assets from " +
                        "misuse, ensure compliance with applicable laws and regulations, and maintain " +
                        "a productive and secure computing environment for all authorized users.",
                        "This policy complements the Information Security Policy (POL-TEST-007) and " +
                        "the Access Control Matrix (CTL-TEST-003); users must comply with all three."
                )),
                new Section("2 Acceptable Uses", List.of(
                        "Company IT resources may be used for legitimate business purposes and for " +
                        "reasonable incidental personal use that does not interfere with job performance " +
                        "or consume significant company resources.",
                        "Users are encouraged to report suspected security incidents, policy violations, " +
                        "or suspicious activity to the IT Security team at it-security@company-test.example."
                )),
                new Section("2.1 Permitted Activities", List.of(
                        "Employees may use company systems to access business applications, " +
                        "communicate with colleagues and clients, conduct research relevant to " +
                        "job responsibilities, and participate in approved training programs.",
                        "The scope of this policy covers all company-owned and company-managed devices, " +
                        "networks, cloud services, and data storage systems, whether accessed on-site " +
                        "or remotely via VPN or other approved remote access solutions.",
                        "Use of personal devices to access company resources is permitted only via " +
                        "the Mobile Device Management (MDM) program and with prior IT Security approval."
                )),
                new Section("3 Prohibited Activities", List.of(
                        "Users must not use company IT resources for activities that are illegal, " +
                        "harmful to the organization, or inconsistent with the company's values " +
                        "and Code of Conduct.",
                        "Repeated or egregious violations will be referred to the Legal department " +
                        "for potential civil or criminal action in addition to disciplinary measures."
                )),
                new Section("3.1 Prohibited Uses", List.of(
                        "The following activities are strictly prohibited: unauthorized access to " +
                        "systems or data; data exfiltration or unauthorized transfer of company data; " +
                        "installation of unlicensed software; cryptocurrency mining on company hardware; " +
                        "bypassing security controls; and accessing or distributing inappropriate content.",
                        "Downloading, storing, or distributing copyrighted materials without proper " +
                        "authorization or license is prohibited and may expose the company to " +
                        "significant legal liability under applicable intellectual property law.",
                        "Using company systems to harass, threaten, or discriminate against any " +
                        "individual is prohibited and constitutes a violation of the Code of Conduct " +
                        "subject to immediate disciplinary action."
                )),
                new Section("4 Enforcement", List.of(
                        "The IT Security team monitors network activity, email, and system access " +
                        "logs for policy compliance; users should have no expectation of privacy " +
                        "when using company IT resources.",
                        "Violations of this policy are investigated by the IT Security and HR teams " +
                        "and may result in suspension of access rights, disciplinary proceedings, or " +
                        "referral to law enforcement where criminal activity is suspected.",
                        "Questions about acceptable use boundaries, or requests for clarification " +
                        "on specific activities, should be directed to it-policy@company-test.example " +
                        "before proceeding with the activity in question."
                )),
                new Section("5 Review and Acknowledgment", List.of(
                        "This policy is reviewed annually by the IT Security Manager and updated to " +
                        "reflect changes in technology, regulatory requirements, and organizational " +
                        "practices; all users must re-acknowledge the updated policy within thirty days.",
                        "New employees and contractors must read and acknowledge this policy before " +
                        "being granted access to company IT resources; acknowledgment is recorded " +
                        "in the HR onboarding system and retained for the duration of employment.",
                        "Policy acknowledgment records are maintained by HR and are available for " +
                        "audit purposes; failure to acknowledge within the required timeframe will " +
                        "result in access suspension until acknowledgment is completed."
                ))
        );
        return fixture("POL-TEST-005", "policy", title, "low", false, sections);
    }

    private PolicyFixture buildSop006() {
        String title = "Customer Data Handling";
        List<Section> sections = List.of(
                new Section("1 Purpose", List.of(
                        "This Standard Operating Procedure (SOP) governs the handling of customer " +
                        "data throughout its lifecycle, from collection through processing, storage, " +
                        "sharing, and ultimate disposal, in compliance with applicable data protection laws.",
                        "All personnel who access, process, or manage customer data must be trained " +
                        "on this SOP before being granted access to customer data systems."
                )),
                new Section("2 Data Classification", List.of(
                        "Customer data is one of the organization's most sensitive assets and must " +
                        "be handled with the highest level of care and discretion at all times.",
                        "Staff must ensure that customer data is not processed beyond the scope " +
                        "of their role and must report any suspected unauthorized access immediately."
                )),
                new Section("2.1 Customer Data Categories", List.of(
                        "Customer data is classified into four categories: Public, Internal, " +
                        "Confidential, and Restricted, based on sensitivity and potential harm " +
                        "from unauthorized disclosure.",
                        "Customer PII, including social security numbers (format 000-00-0000), " +
                        "email addresses such as customer-test-001@company-test.example, payment " +
                        "card numbers, and account credentials, is classified as Restricted data.",
                        "Restricted customer data must be treated with the highest level of security " +
                        "controls and accessed only by personnel with an explicit, documented " +
                        "business need approved by the Data Protection Officer."
                )),
                new Section("3 Handling Procedures", List.of(
                        "All personnel handling customer data must complete annual training on data " +
                        "protection requirements and must sign a data handling confidentiality agreement.",
                        "Any suspicious activity involving customer data must be reported immediately " +
                        "to the Data Protection Officer at dpo@company-test.example."
                )),
                new Section("3.1 Storage Requirements", List.of(
                        "All customer PII must be encrypted at rest using AES-256 and in transit " +
                        "using TLS 1.3 or higher; legacy encryption protocols (TLS 1.0, TLS 1.1, " +
                        "SSL) are prohibited for all customer data transmissions.",
                        "Customer data must be stored only in systems listed on the approved Data " +
                        "Register; storage of customer PII on personal devices, portable media, or " +
                        "unapproved cloud services is strictly prohibited without written authorization.",
                        "Database systems containing customer PII must implement row-level security " +
                        "and field-level encryption for SSN and payment card data, with access " +
                        "audit logging enabled and reviewed weekly by the Database Security team."
                )),
                new Section("3.2 Access Controls", List.of(
                        "Only authorized personnel may access, view, or share customer data within " +
                        "the explicit scope of their documented role; access must be granted on a " +
                        "need-to-know basis and reviewed quarterly by the Data Protection Officer.",
                        "The Data Protection Officer, Maria Rivera-TEST (dpo@company-test.example, " +
                        "(555) 000-0008, EMP-ID: 00020, SSN: 000-00-0006), must approve in writing " +
                        "any request to disclose customer data to third parties, including partners, " +
                        "regulators, and law enforcement, prior to any such disclosure.",
                        "All access to Restricted customer data is logged with user identity, " +
                        "timestamp, data accessed, and stated business purpose; these logs are " +
                        "retained for three (3) years and are subject to quarterly audit review."
                )),
                new Section("4 Regulatory Compliance", List.of(
                        "The organization processes customer data subject to multiple regulatory " +
                        "frameworks, and compliance with all applicable requirements is mandatory.",
                        "Failure to comply with regulatory requirements may result in significant " +
                        "financial penalties, regulatory sanctions, and reputational harm."
                )),
                new Section("4.1 GDPR Requirements", List.of(
                        "Processing of EU customer data must comply with GDPR requirements, including " +
                        "the lawful basis requirements of Article 6, data minimization under Article 5, " +
                        "and breach notification obligations under Article 33.",
                        "Data Subject Access Requests from EU customers must be fulfilled within " +
                        "thirty (30) days per GDPR compliance requirements; extensions are permitted " +
                        "only with notification to the data subject within the initial thirty-day period.",
                        "Transfers of EU customer data to third countries require appropriate safeguards " +
                        "such as Standard Contractual Clauses or Binding Corporate Rules approved " +
                        "by the relevant supervisory authority."
                )),
                new Section("4.2 PCI-DSS Requirements", List.of(
                        "Cardholder data must be handled in accordance with PCI-DSS compliance " +
                        "requirements applicable to the organization's merchant level classification.",
                        "Primary Account Numbers (PAN) must not be stored unencrypted after " +
                        "transaction authorization; CVC/CVV data must never be stored post-authorization " +
                        "regardless of encryption status, per PCI-DSS Requirement 3.",
                        "Annual PCI-DSS compliance assessments must be conducted by a Qualified Security " +
                        "Assessor (QSA); the most recent assessment report is maintained by " +
                        "pci-compliance@company-test.example."
                )),
                new Section("5 Incident Response", List.of(
                        "Any suspected or confirmed customer data breach must be reported immediately " +
                        "to the Data Protection Officer at dpo@company-test.example and the " +
                        "Incident Response Team at incident-response@company-test.example.",
                        "Customer data breach incidents are managed under the Incident Response " +
                        "Procedure (SOP-TEST-002) with additional regulatory notification requirements " +
                        "under GDPR, PCI-DSS, and applicable state breach notification laws.",
                        "The organization maintains cyber liability insurance with a minimum coverage " +
                        "of five million dollars ($5,000,000) to mitigate financial risk from " +
                        "customer data breach incidents; policy contact: insurance@company-test.example."
                ))
        );
        return fixture("SOP-TEST-006", "sop", title, "high", true, sections);
    }

    private PolicyFixture buildPol007() {
        String title = "Information Security Policy";
        List<Section> sections = List.of(
                new Section("1 Overview", List.of(
                        "This policy establishes the information security framework for the organization, " +
                        "defining the principles, responsibilities, and controls required to protect " +
                        "information assets from unauthorized access, disclosure, modification, and destruction.",
                        "Information security is a shared organizational responsibility; all personnel " +
                        "are accountable for protecting the information assets to which they have access."
                )),
                new Section("2 Information Classification", List.of(
                        "All organizational information assets must be classified according to their " +
                        "sensitivity and the potential harm that could result from unauthorized disclosure.",
                        "Information owners are responsible for assigning and maintaining the " +
                        "appropriate classification and for ensuring controls are commensurate with risk."
                )),
                new Section("2.1 Classification Levels", List.of(
                        "Information is classified into four levels: Public, Internal, Confidential, " +
                        "and Top Secret. Classification determines the required security controls, " +
                        "handling procedures, and authorized disclosure channels.",
                        "The scope of this policy applies to all company information assets regardless " +
                        "of format or storage medium, including digital data, physical documents, and " +
                        "verbal communications in business contexts.",
                        "Reclassification of information may be performed only by the information owner " +
                        "or designated delegate with documented justification retained in the " +
                        "Information Asset Register."
                )),
                new Section("3 Security Controls", List.of(
                        "All systems containing Confidential or Top Secret information must implement " +
                        "multi-factor authentication, role-based access control, and comprehensive " +
                        "audit logging with tamper-evident storage.",
                        "Access reviews must be conducted quarterly for all privileged accounts and " +
                        "annually for all standard user accounts, with findings documented and " +
                        "remediated within thirty (30) days.",
                        "Vulnerability assessments must be conducted monthly and penetration tests " +
                        "annually; critical and high-severity vulnerabilities must be remediated " +
                        "within fifteen (15) and thirty (30) days respectively."
                )),
                new Section("4 Breach Reporting", List.of(
                        "Any suspected or confirmed information security breach must be reported " +
                        "immediately upon discovery to limit damage and enable timely regulatory " +
                        "notification where required.",
                        "Delayed reporting of known breaches is a serious disciplinary matter and " +
                        "may expose the organization to enhanced regulatory penalties."
                )),
                new Section("4.1 Reporting Timeline", List.of(
                        "All data breaches must be reported within seventy-two (72) hours of " +
                        "discovery to the Information Security team, regardless of whether the full " +
                        "scope of the breach has been determined, using Breach Notification Form BN-001.",
                        "Where HIPAA applies to the breached data, additional reporting to the " +
                        "Department of Health and Human Services (HHS) is required within sixty (60) " +
                        "days of discovery per the HIPAA Breach Notification Rule.",
                        "Initial breach reports must include all available information at the time of " +
                        "reporting; supplementary details may be submitted within thirty (30) days " +
                        "as the investigation progresses."
                )),
                new Section("4.2 Notification Requirements", List.of(
                        "Breach notifications to affected individuals and regulators must include: " +
                        "the date and nature of the breach; the categories and approximate number of " +
                        "data records affected; the individuals or groups impacted; and remediation steps.",
                        "HIPAA-covered entities must also report breaches affecting five hundred (500) " +
                        "or more individuals to the Department of Health and Human Services within " +
                        "sixty (60) days and provide individual notifications without unreasonable delay.",
                        "All breach notifications must be reviewed by the Legal team before issuance; " +
                        "legal counsel contact is legal@company-test.example, (555) 000-0009."
                )),
                new Section("5 Exceptions", List.of(
                        "All information security policies and controls are mandatory; exceptions " +
                        "may be granted only in genuinely exceptional circumstances with appropriate " +
                        "compensating controls to maintain an equivalent level of security.",
                        "Exception requests that constitute an override of a mandatory control or a " +
                        "bypass of a security requirement must demonstrate equivalent or greater " +
                        "security through an alternative approach approved by the CISO."
                )),
                new Section("5.1 Exception Process", List.of(
                        "Exceptions to security controls require a written Exception Request submitted " +
                        "to the CISO via the Security Governance portal, with full risk assessment, " +
                        "proposed compensating controls, and a defined expiry date.",
                        "All exception requests must document the risk acceptance position and must " +
                        "not constitute an override of the policy or a bypass of a mandatory " +
                        "requirement without the written approval of the CISO and the relevant " +
                        "business unit head.",
                        "Approved exceptions are registered in the Exception Log, reviewed quarterly, " +
                        "and automatically expire after one hundred and eighty (180) days unless " +
                        "explicitly renewed with fresh CISO approval."
                )),
                new Section("6 Compliance Monitoring", List.of(
                        "Compliance with this policy is monitored through quarterly security audits, " +
                        "continuous automated control monitoring, and annual third-party assessments " +
                        "conducted by an independent security firm.",
                        "Non-compliance findings are tracked in the Risk Register and assigned to " +
                        "a named remediation owner with a defined target date; overdue findings are " +
                        "escalated to the Board Security Committee monthly.",
                        "This policy is owned by the CISO and reviewed annually; the current " +
                        "version is maintained at policy@company-test.example, (555) 000-0010."
                ))
        );
        return fixture("POL-TEST-007", "policy", title, "low", true, sections);
    }

    private PolicyFixture buildFaq008() {
        String title = "IT Support FAQ";
        List<Section> sections = List.of(
                new Section("1 Password Reset", List.of(
                        "Password-related issues are among the most common IT support requests. " +
                        "This section provides self-service options to minimize helpdesk wait times " +
                        "and enable employees to resolve common authentication issues independently.",
                        "If self-service options do not resolve the issue, contact the IT Helpdesk " +
                        "at helpdesk@company-test.example or (555) 000-0011."
                )),
                new Section("1.1 Self-Service Password Reset", List.of(
                        "Q: How do I reset my company account password when I am locked out or " +
                        "have forgotten my current credentials?",
                        "A: Navigate to the IT Self-Service portal at https://it.company-test.example " +
                        "and click Reset Password. Enter your employee email address and follow the " +
                        "multi-step verification process; your new password will be active immediately.",
                        "Q: My account is locked after too many failed login attempts. How do I " +
                        "unlock it without calling the helpdesk?",
                        "A: Account lockouts can be resolved via the Self-Service portal using your " +
                        "backup verification method (SMS or authenticator app). If neither method is " +
                        "available, contact helpdesk@company-test.example with your employee ID."
                )),
                new Section("2 Software Requests", List.of(
                        "All software running on company devices must be approved and licensed through " +
                        "the IT Software Catalogue; installation of unapproved software is a violation " +
                        "of the Acceptable Use Policy (POL-TEST-005).",
                        "The Software Catalogue is available on the IT intranet; employees should " +
                        "check the catalogue before submitting a new software request."
                )),
                new Section("2.1 Software Installation Requests", List.of(
                        "Q: How do I request installation of software that is not currently available " +
                        "in the IT Software Catalogue?",
                        "A: Submit a Software Installation Request via the IT Service Desk portal at " +
                        "https://servicedesk.company-test.example, providing the software name, " +
                        "version, vendor, business justification, and cost estimate. Standard requests " +
                        "are reviewed and processed within three (3) business days.",
                        "Q: Can I install free or open-source software on my company laptop without " +
                        "going through the formal request process?",
                        "A: No. All software, including free and open-source tools, must be approved " +
                        "through the IT Service Desk process regardless of cost. Unapproved software " +
                        "may be removed by IT without notice and the incident reported to the user's manager."
                )),
                new Section("3 Hardware Issues", List.of(
                        "For hardware issues, contact the IT Helpdesk at helpdesk@company-test.example " +
                        "or (555) 000-0011. Include the asset tag number from the sticker on your device.",
                        "Loaner equipment is available for critical roles when primary equipment is " +
                        "under repair; request via the IT Service Desk portal citing business urgency.",
                        "All hardware faults are triaged within four (4) business hours; critical " +
                        "roles impacted by hardware failure receive priority service with a target " +
                        "resolution time of twenty-four (24) hours."
                )),
                new Section("4 Remote Access", List.of(
                        "Remote access to company systems is available to all employees with a valid " +
                        "business need, subject to completion of the Remote Access Agreement and " +
                        "approval from the employee's line manager.",
                        "All remote access sessions are encrypted and logged; users must not share " +
                        "VPN credentials or allow unauthorized individuals to access company systems " +
                        "via their remote access connection."
                )),
                new Section("4.1 VPN Setup and Troubleshooting", List.of(
                        "Q: How do I set up remote access to connect to company systems from " +
                        "home or while travelling?",
                        "A: Download and install the company VPN client from the IT Self-Service " +
                        "portal at https://it.company-test.example/vpn. Authenticate using your " +
                        "company credentials and complete MFA verification to establish the tunnel.",
                        "Q: My VPN connection keeps dropping when I work remotely. What should " +
                        "I try before contacting the helpdesk?",
                        "A: First check your internet connection stability. If the issue persists, " +
                        "reinstall the VPN client from the IT portal. For ongoing issues, raise a " +
                        "ticket at helpdesk@company-test.example with connection logs attached."
                )),
                new Section("5 Escalation and Support Hours", List.of(
                        "The IT Helpdesk operates Monday to Friday, 08:00 to 18:00 local time. " +
                        "Out-of-hours critical support is available for business-critical systems; " +
                        "contact the on-call line at (555) 000-0012 for Priority 1 incidents only.",
                        "Response time targets: Priority 1 (system down, business critical) — " +
                        "thirty (30) minutes; Priority 2 (significant impairment) — two (2) hours; " +
                        "Priority 3 (minor issue) — next business day.",
                        "For unresolved tickets older than five (5) business days, escalate by " +
                        "emailing it-escalations@company-test.example with the ticket number " +
                        "and a description of the business impact."
                ))
        );
        return fixture("FAQ-TEST-008", "faq", title, "low", false, sections);
    }

    // ── shared builder ─────────────────────────────────────────────────────────

    private static PolicyFixture fixture(
            String documentId, String docType, String title,
            String piiDensity, boolean highRisk, List<Section> sections) {
        try {
            byte[] pdf = new PdfBuilder().build(title, sections);
            Map<String, Object> meta = Map.of(
                    "docType",     docType,
                    "piiDensity",  piiDensity,
                    "highRisk",    highRisk,
                    "version",     "1.0",
                    "status",      "active"
            );
            return new PolicyFixture(documentId, docType, title, pdf, meta);
        } catch (IOException e) {
            throw new RuntimeException("Failed to build PDF for " + documentId, e);
        }
    }

    // ── PDF builder ────────────────────────────────────────────────────────────

    /** Section heading plus its body paragraphs. */
    private record Section(String heading, List<String> paragraphs) {}

    /**
     * Builds a multi-page A4 PDF from a list of sections.
     *
     * <p>Layout:
     * <ul>
     *   <li>Document title: HELVETICA_BOLD 16 pt at top of first page.</li>
     *   <li>Section headings: HELVETICA_BOLD 12 pt, preceded and followed by a
     *       28 pt gap so that {@link org.apache.pdfbox.text.PDFTextStripper}
     *       produces {@code \n\n} separators that
     *       {@link com.policyguard.service.chunking.ChunkingService} can split on.</li>
     *   <li>Body lines: HELVETICA 11 pt, wrapped at {@value #WRAP_COLS} chars.</li>
     *   <li>Between-paragraph gap: 28 pt (2× LINE_H) → also yields {@code \n\n}.</li>
     *   <li>Auto-pagination when remaining height {@literal <} {@value #BOTTOM_MARGIN}.</li>
     * </ul>
     */
    private static class PdfBuilder {

        private static final float PAGE_TOP    = PDRectangle.A4.getHeight(); // ~841.9
        private static final float LEFT_MARGIN = 50f;
        private static final float TOP_MARGIN  = 50f;
        private static final float BOTTOM_MARGIN = 60f;
        private static final float LINE_H      = 14f;
        /** Extra gap added AFTER a section heading (total gap to body = 2 × LINE_H). */
        private static final float SECTION_EXTRA = 14f;
        /** Extra gap added AFTER the last line of each paragraph (total inter-para = 2 × LINE_H). */
        private static final float PARA_EXTRA  = 14f;
        /** Wrap body text at this many characters. */
        private static final int   WRAP_COLS   = 92;

        private PDDocument           doc;
        private PDPageContentStream  stream;
        private float                curY;

        /** Fonts — created fresh per document to avoid cross-document sharing issues. */
        private PDType1Font fontBold;
        private PDType1Font fontRegular;

        byte[] build(String docTitle, List<Section> sections) throws IOException {
            doc         = new PDDocument();
            fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            openPage();

            // Document title
            writeLine(docTitle, fontBold, 16);
            curY -= (LINE_H + SECTION_EXTRA + SECTION_EXTRA); // larger gap under title

            for (Section sec : sections) {
                ensureSpace(LINE_H * 5); // keep heading + at least 2 body lines together
                writeLine(sec.heading(), fontBold, 12);
                curY -= (LINE_H + SECTION_EXTRA);

                for (String para : sec.paragraphs()) {
                    List<String> lines = wrapText(para);
                    for (String line : lines) {
                        ensureSpace(LINE_H);
                        writeLine(line, fontRegular, 11);
                        curY -= LINE_H;
                    }
                    curY -= PARA_EXTRA; // blank gap → PDFTextStripper emits \n\n
                }
                curY -= SECTION_EXTRA; // extra breathing room between sections
            }

            stream.close();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            doc.close();
            return baos.toByteArray();
        }

        private void openPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            curY   = PAGE_TOP - TOP_MARGIN;
        }

        private void ensureSpace(float needed) throws IOException {
            if (curY - needed < BOTTOM_MARGIN) {
                stream.close();
                openPage();
            }
        }

        /**
         * Writes a single text run at the current {@link #curY} position.
         * Does NOT advance {@code curY} — caller is responsible.
         */
        private void writeLine(String text, PDType1Font font, float fontSize) throws IOException {
            if (text == null || text.isEmpty()) return;
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(LEFT_MARGIN, curY);
            stream.showText(sanitize(text));
            stream.endText();
        }

        /** Remove characters that PDFBox Standard14 fonts cannot encode (keep ASCII printable). */
        private static String sanitize(String s) {
            StringBuilder sb = new StringBuilder(s.length());
            for (char c : s.toCharArray()) {
                if (c >= 32 && c < 127) {
                    sb.append(c);
                } else {
                    sb.append(' ');
                }
            }
            return sb.toString();
        }

        /** Wraps text at {@value #WRAP_COLS} characters on word boundaries. */
        private static List<String> wrapText(String text) {
            List<String> lines = new ArrayList<>();
            String[] words = text.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                if (cur.length() > 0 && cur.length() + 1 + word.length() > WRAP_COLS) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    if (!cur.isEmpty()) cur.append(' ');
                    cur.append(word);
                }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
            if (lines.isEmpty())  lines.add("");
            return lines;
        }
    }
}

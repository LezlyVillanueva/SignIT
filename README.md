# SignIT

> **Centralized Internal Document Signing & Clearance Hub**

A desktop-based document signing and clearance management system developed for the **University of the Immaculate Conception (UIC)**. SignIT aims to streamline internal document processing by replacing paper-based clearance procedures with a centralized digital workflow.

## 📌 Project Overview

SignIT addresses common problems in campus document processing, including:

* Delays caused by physical clearance processing.
* Lost or misplaced paper forms.
* Manual office-to-office document routing.
* Lack of transparency in approval progress.
* Difficulty tracking document revisions and approval history.

Through a centralized platform, SignIT enables students and applicants to submit clearance requests, faculty members to review and approve documents, and administrators to manage accounts, permissions, and system activities.

---

## 🔄 Replacing Legacy Workflows (Gmail, iLovePDF, & Ad-hoc Tools)

Currently, students and faculty often rely on a fragmented, manual digital process that creates delays, security risks, and administrative confusion:

```
[ Traditional Workflow ]
Student drafts document ➔ Emails PDF via Gmail ➔ Approver downloads PDF 
➔ Opens external site (iLovePDF, Smallpdf, Preview) ➔ Adds visual signature / merges pages 
➔ Re-downloads PDF ➔ E-mails back via Gmail ➔ Repeat for next approver
```

### ❌ Challenges with the Old Workflow:
* **Inbox Chaos & Lost Emails:** Important clearance requests get buried under high daily email volume.
* **Manual Forwarding Bottlenecks:** Students must manually determine who to email next and wait for replies without visibility into delays.
* **Third-Party Security Risks:** Uploading confidential university documents and signatures to free external online PDF tools (e.g., iLovePDF) introduces potential privacy and data leakage risks.
* **No Integrity Checks or Audit Trail:** Email attachments can be accidentally modified, replaced, or forwarded to wrong addresses with no central log or cryptographic hash verification.
* **Complex Revision Cycles:** If a revision is needed, tracking back-and-forth email chains with multiple modified file attachments becomes chaotic.

### ✅ The SignIT Solution:
SignIT consolidates this entire pipeline into a single, unified, and secure platform:

```
[ SignIT Integrated Workflow ]
Student uploads document & sets route ➔ SignIT routes automatically 
➔ Approver signs in-app with 1 click ➔ Automatic forward to next role ➔ Complete with audit trail
```

| Feature / Step | Traditional Workflow (Gmail + External PDF Tools) | SignIT Platform |
| :--- | :--- | :--- |
| **Document Routing** | Manual emailing from inbox to inbox | Automated sequential workflow routing |
| **Signing Process** | Download file ➔ Upload to iLovePDF/Smallpdf ➔ Sign ➔ Re-download ➔ Email | Native single-click digital signing directly inside the app |
| **Status Visibility** | Zero visibility (Students must email to ask for updates) | Real-time status dashboard showing current stage & assigned approver |
| **Data Security** | Third-party web tools process private documents | Local & institutional database handling with no third-party exposure |
| **Rejections & Edits** | Confusing email threads with multiple file versions | Structured Step-Back / Full-Reset revision options with contextual comments |
| **Auditability** | Scattered email headers | Append-only immutable audit ledger with SHA-256 integrity verification |

---

## 🎯 Project Objectives

* **Digitize** internal document signing and clearance procedures.
* **Automate** sequential document routing and approval.
* **Provide** real-time visibility into document status.
* **Support** electronic signatures and document integrity verification.
* **Maintain** an audit trail of document transactions.
* **Improve** accountability, efficiency, and transparency across campus departments.

## 🛠️ Technologies Used

| Component | Technology | 
| ----- | ----- | 
| **Programming Language** | Java | 
| **Java Development Kit** | JDK 17 or later | 
| **User Interface** | Java Swing | 
| **UI Look and Feel** | FlatLaf | 
| **Database** | MySQL or SQLite | 
| **Database Connectivity** | JDBC | 
| **Build & Dependency Management** | Apache Maven | 
| **Software Architecture** | Model-View-Controller (MVC) | 
| **Programming Approach** | Object-Oriented Programming (OOP) | 
| **Cryptographic Hashing** | SHA-256 | 

## ✨ Key Features

### 1. Secure Authentication and Role Management

* **Account Verification:** Newly registered accounts remain inactive until reviewed and approved by a Super Admin.
* **Role-Based Access Control (RBAC):** Provides distinct dashboards and permissions for Students/Applicants, Faculty/Approvers, and Super Admins.
* **Role Elevation Requests:** Allows eligible users to request additional approver privileges for administrative review.

### 2. Dynamic Multi-Level Document Routing

* **Sequential Approval Workflow:** Allows document owners to configure an approval sequence based on institutional requirements.
* **Automatic Forwarding:** Approved documents are automatically forwarded to the next assigned reviewer.
* **Status Tracking:** Displays the current approval stage, pending reviewer, completed approvals, and overall progress.

```
Student ➔ Adviser ➔ Department Chair ➔ Dean ➔ Completed
```

*(Note: The actual approval sequence depends on the configured workflow.)*

### 3. Electronic Signatures and Verification

* **Stored Electronic Signatures:** Allows users to upload or capture a signature for use when approving documents.
* **Single-Click Signing:** Enables authorized approvers to apply stored signatures to pending documents.
* **Cryptographic Hashing:** Uses SHA-256 to generate a hash based on signature transaction data (e.g., user ID, document ID, timestamp, signature data).
* **Integrity Verification:** Supports checking whether recorded signature transaction data has changed.

> **Note:** A SHA-256 hash alone does not establish legal non-repudiation or prove signer identity. Stronger guarantees require appropriate authentication, protected signing keys, and reliable audit records.

### 4. Dual Rejection and Revision Workflow

SignIT supports two document rejection workflows:

* **Step-Back Rejection:** Returns a document to the immediately preceding reviewer for minor corrections while preserving the remaining approval workflow.
* **Full Reset Rejection:** Returns the document to the requester for major revisions and restarts the approval process from the beginning. Previous approval records are retained in the audit history.

*Both options allow approvers to provide comments explaining requested changes or rejections.*

### 5. Immutable Audit Ledger

The system maintains an append-only transaction log intended to improve accountability and transparency. Recorded details include:

* Transaction timestamps
* User identification and available IP logs
* Actions performed (approvals, rejections, revisions)
* Comments and feedback
* Document workflow events

## 👥 User Roles and Modules

### 👨‍🎓 Student / Applicant

* Submit clearance requests and attach required digital files.
* Define the sequence of document reviewers.
* Monitor document progress and approval status.
* View reviewer feedback and revision comments.

### 👨‍🏫 Faculty / Approver

* View and manage pending document requests assigned to them.
* Apply stored electronic signatures.
* Approve documents or reject them for revision.
* Can create documents.
* Provide contextual comments and feedback.

### 🛡️ Super Admin

* Review and approve pending user registrations.
* Manage role-elevation requests.
* Access system-wide transaction logs.
* Manage user permissions and system configurations.

## 🔄 General System Workflow

1. **Account Registration:** User registers for a SignIT account.
2. **Account Verification:** Super Admin reviews and approves the registration.
3. **Document Submission:** Student or applicant submits a clearance request and required files.
4. **Reviewer Configuration:** Requester establishes the required approval sequence.
5. **Document Review:** Assigned approver reviews the document and provides a decision.
6. **Approval & Forwarding:** Approved documents proceed automatically to the next reviewer.
7. **Revision or Rejection:** Documents requiring corrections are returned according to the selected rejection workflow.
8. **Completion:** Once all required approvals are collected, the request is marked complete.
9. **Audit Recording:** Relevant actions are logged throughout the process.

## 🏗️ System Architecture

SignIT follows the **Model-View-Controller (MVC)** architectural pattern to separate UI, application logic, and data management:

* **Model:** Represents application data, entities, and database operations.
* **View:** Provides the Java Swing interface and user dashboards.
* **Controller:** Processes user actions, coordinates application logic, and manages interactions between Views and Models.

## 🔐 Security Considerations

* Account approval before newly registered users gain access.
* Role-based access restrictions (RBAC).
* SHA-256 hashing for signature transaction integrity checks.
* Controlled access to document approval and administrative functions.
* Audit logging of important user actions.

> 🔒 **Security Recommendation:** Passwords should be stored using a salted, adaptive password-hashing algorithm (e.g., Argon2id, bcrypt, or PBKDF2) rather than plain SHA-256. Database access, stored signatures, uploaded documents, and audit records should also be protected with appropriate access controls.

## 💻 System Requirements

* **Java Development Kit (JDK):** 17 or later
* **Build Tool:** Apache Maven
* **Database:** MySQL or SQLite
* **Driver:** JDBC driver compatible with the selected database
* **OS:** Windows / macOS / Linux capable of running Java Swing applications

## ⚙️ Installation and Setup

### Prerequisites

Install the required **JDK 17+** and **Apache Maven**, then configure your intended database.

### 1. Clone the Repository

```bash
git clone https://github.com/LezlyVillanueva/SignIT.git
cd SignIT
```

### 2. Configure the Database

* Create or initialize the required database.
* Configure database connection settings in the application properties.
* Ensure JDBC driver dependencies are available.
* Apply project database schema / initialization scripts if provided.

### 3. Build the Project

Run the following command from the folder containing `pom.xml`:

```bash
mvn clean install
```

### 4. Run the Application

If using the Maven Exec Plugin:

```bash
mvn exec:java
```

*Alternatively, run the main class directly within your IDE (VS Code / IntelliJ).*

## ⚠️ Current Limitations

* Designed primarily as a local desktop application using Java Swing.
* Uses local database persistence or a configured database connection.
* Does not currently include external real-time email or SMS notifications.
* Requires further security validation to ensure strong signature verification and audit integrity.

## 🚀 Future Enhancements

* **Remote Cloud Database Synchronization:** Integration with AWS RDS or a remote MySQL server.
* **Mobile Companion Application:** Support for mobile access, push notifications, and remote approvals.
* **Email and SMS Notifications:** Automated alerts for document submissions, approvals, rejections, and revisions.
* **Enhanced Audit Integrity:** Stronger cryptographic protections against unauthorized modification of transaction records.

## 👥 Authors

**Project Title:** SignIT – Centralized Internal Document Signing & Clearance Hub  
**Institution:** University of the Immaculate Conception  
**College:** College of Computer Studies (CCS)  
**Program:** Bachelor of Science in Information Technology (BSIT)  

* **Acosto, Jefferson V.**
* **Dumangas, Lian T.**
* **Po, Oliver Vince G.**
* **Villanueva, Lezly Therese G.**

---

*SignIT — Simplifying document clearance through centralized digital signing and approval workflows.*

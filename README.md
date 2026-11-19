# PG Resident Management System

## About the Project
A web application that helps PG owners manage resident records.

## Features
- Owner registration with administrator approval
- Separate admin login for approving PG owner accounts
- Owner Login (accounts are enabled only after approval)
- Add Resident
- View Resident Details
- Update Resident Details
- Delete Resident Records
- Room-sharing capacity and available-bed tracking by room number
- Owner-scoped resident search with server-side pagination
- Database-backed resident, room, bed, and monthly rent dashboard totals

## Performance logging
The backend logs API request durations, signup lookup/hash/save timings, and password hashing/verification durations without logging passwords or request bodies. `SLOW_REQUEST_THRESHOLD_MS` controls the warning threshold and defaults to `1000` milliseconds. These timings identify where time is spent; they do not reduce the work required by secure BCrypt password hashing.

## Admin setup
Set the backend environment variables `ADMIN_EMAIL` and `ADMIN_PASSWORD` to configure the admin email and initial password. On first startup, the backend stores the admin email and a password hash in the database; a password changed through admin recovery remains active after restart, so changing `ADMIN_PASSWORD` later does not overwrite the database credential. Use the frontend's **Admin portal** to review registrations, approve owners, issue owner recovery IDs, or permanently delete owner data. Deletion erases the owner's account, residents, rent payments, rooms, and stored resident documents; it cannot be undone. Email addresses and mobile numbers from deleted accounts can be used to register again. For admin recovery, choose **Forgot password?**, submit the admin email, and retrieve the generated one-time ID from protected backend logs. It expires after 24 hours and works once. Keep backend logs private; the ID is a credential. Owner recovery IDs are issued by an administrator and also expire after 24 hours. Hibernate updates the account tables on startup through the existing `ddl-auto=update` configuration. Owner registration collects username, mobile number, email, property name and type, locality, city, and password before administrator approval.

Owner and admin credentials are held in tab-scoped session storage so authenticated pages and the selected dashboard section survive a browser refresh; signing out clears the saved session. Owners can edit their registered profile details with **My profile**. After a successful registration, the page polls for administrator approval and moves to sign-in automatically once approved. The owner dashboard shows database-backed resident, room, bed, monthly rent totals, and occupancy/collection visualizations. Resident search and status filtering use a debounced, owner-scoped, server-paginated API; the existing list endpoints remain available for compatibility. Select a resident to open the responsive details drawer with contact information, addresses, rent history, documents, and actions. Add every room with its sharing capacity (single through four sharing) in **Manage rooms/beds** to include vacant rooms in availability. Existing resident rooms are also counted; resident sharing must match a configured room. Available-bed totals include configured capacity minus active residents.

## Documents and account recovery
Resident photos and identity proofs are stored in the MySQL database in 16 KB chunks so uploads fit within regular `BLOB` column limits. The startup migration widens undersized document content columns to `MEDIUMBLOB` without discarding existing file data. New uploads no longer depend on an instance-local filesystem. Existing documents that were stored on a previous server's local filesystem may need to be uploaded again if that server's files are no longer available.

Owners sign in with email and password. For recovery, an administrator generates a one-time recovery ID from the owner-management page and shares it directly with that owner. The owner selects **Forgot password?**, enters the ID and a new password, and submits the form. Recovery IDs expire after 24 hours, are stored hashed in the database, and can be used once.

## Technologies Used
- Java 21
- Spring Boot
- Spring Data JPA
- MySQL
- Maven

## Future Enhancements
- QR Code-Based Registration
- Resident Login

## Project Status
Under Development

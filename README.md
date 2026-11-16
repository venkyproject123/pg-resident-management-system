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
Configure a unique `ADMIN_USERNAME` and a strong `ADMIN_PASSWORD` as private environment variables on the backend service before deploying. Use the frontend's **Admin login** link to review waiting registrations and approved owners, approve access, or remove owner access. Removing an owner is a soft removal: the account is hidden from the admin lists and can no longer sign in, while associated resident and payment history is preserved. Hibernate updates the `owner_accounts` table on startup through the existing `ddl-auto=update` configuration. Owner registration collects owner name, mobile number, email, property name and type, locality, city, and password before administrator approval.

Owner and admin credentials are held in memory only and are not persisted in browser storage; refresh the page to sign in again. The owner dashboard shows database-backed resident, room, bed, and monthly rent totals. Resident search and status filtering use a debounced, owner-scoped, server-paginated API; the existing list endpoints remain available for compatibility. Select a resident to open the responsive details drawer with contact information, addresses, rent history, documents, and actions. Add every room with its sharing capacity (single through four sharing) in **Manage rooms/beds** to include vacant rooms in availability. Existing resident rooms are also counted; resident sharing must match a configured room. Available-bed totals include configured capacity minus active residents.

## Documents and account recovery
Resident photos and identity proofs are stored in the MySQL database so they remain available across backend restarts and deployments. New uploads no longer depend on an instance-local filesystem. Existing documents that were stored on a previous server's local filesystem may need to be uploaded again if that server's files are no longer available. Password recovery and messaging notifications are unavailable. Contact the administrator for account recovery.

## Mobile OTP sign-in
Owners can sign in with email and password or with a mobile OTP using the number supplied during registration. Configure the following private backend environment variables to enable SMS delivery through Twilio: `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, and `TWILIO_FROM_NUMBER` (an E.164 sender number such as `+14155552671`). OTPs expire after five minutes, allow at most five verification attempts, and requests for any number are limited to once per minute to avoid revealing whether a number has an account. OTPs and sign-in tokens are held only in backend memory; the browser does not persist the OTP session.

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

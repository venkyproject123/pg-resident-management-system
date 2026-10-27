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

## Admin setup
Configure a unique `ADMIN_USERNAME` and a strong `ADMIN_PASSWORD` as private environment variables on the backend service before deploying. Use the frontend's **Admin login** link to review waiting registrations and approved owners, and approve access. The admin screen shows separate waiting-approval and approved-owner columns with each owner's PG name, username, mobile number, and location/area. New owners register with their PG name, location/area, username, password (minimum 12 characters), and mobile number; registration no longer uses an admin OTP.

Owner and admin sign-ins survive a page refresh within the same browser tab session. The resident dashboard opens on active residents and has separate active/vacated totals; search and status filtering run locally for immediate results. On mobile, residents are shown as compact cards with the remaining details and actions available under **All details**. Resident records include a room sharing size (single through four sharing); all residents assigned to a room must use the same capacity. Use **Empty beds** to show rooms with available beds, calculated from that capacity and active residents. Older residents without a sharing size must be edited to set one before their room appears in availability.

## Documents and account recovery
Resident photos and identity proofs are stored in the MySQL database so they remain available across backend restarts and deployments. New uploads no longer depend on an instance-local filesystem. Existing documents that were stored on a previous server's local filesystem may need to be uploaded again if that server's files are no longer available. WhatsApp Cloud API messaging has been removed; password recovery and WhatsApp notifications are unavailable. Contact the administrator for account recovery.

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

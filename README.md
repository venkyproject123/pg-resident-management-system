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

Owner and admin sign-ins survive a page refresh within the same browser tab session. Resident records include a room sharing size (single through four sharing); all residents assigned to a room must use the same capacity. Available beds are calculated from that capacity and active residents. Older residents without a sharing size must be edited to set one before their room appears in availability.

## Rent reminders
Rent reminders are scheduled for 9:00 AM India time on the day before each active resident's monthly due date, calculated from their joining day (clamped to the month's last day). The approved WhatsApp reminder template configured as `WHATSAPP_RENT_REMINDER_TEMPLATE` should describe the payment as due tomorrow; its third body variable is the rent period (for example, `NOVEMBER 2026`).

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

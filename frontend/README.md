# Frontend

React + TypeScript application for the Telegram Mini App. It checks backend connectivity and exchanges raw Telegram `initData` for a backend session when opened inside Telegram. It obtains a CSRF token for state-changing API requests. Authenticated customers can select an active service category, submit a lead, and see the returned reference number. The manager workspace and customer lead list remain planned.

From the frontend directory:

```powershell
npm.cmd install
npm.cmd run dev
```

Vite proxies /api requests to the local backend at http://localhost:8080. Device testing and production use require an HTTPS Mini App URL. The backend verifies Telegram `initData`; the frontend does not trust `initDataUnsafe`.

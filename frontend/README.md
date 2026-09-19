# Frontend

React + TypeScript application for the Telegram Mini App. It currently provides a starter screen and checks backend connectivity. The customer form and manager workspace will be added in later phases.

From the frontend directory:

```powershell
npm.cmd install
npm.cmd run dev
```

Vite proxies /api requests to the local backend at http://localhost:8080. Production use will require an HTTPS Mini App URL and backend verification of Telegram initData.

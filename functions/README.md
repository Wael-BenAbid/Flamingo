# Cloud Function: createStaffAccount

This callable Cloud Function creates a Firebase Auth user with a temporary password and writes an `employees/{uid}` document.

Deployment:

1. Install dependencies:

```bash
cd functions
npm install
```

2. Set allowed admin emails as an environment variable (comma-separated):

```bash
firebase functions:config:set repo.admin_emails="waelbenabid1@gmail.com,abidos.games@gmail.com"
```

3. Deploy:

```bash
firebase deploy --only functions:createStaffAccount
```

Client usage (Web/Android): use callable functions SDK and call `createStaffAccount` with `{ email, displayName, role }`.

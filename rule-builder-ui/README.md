# Rule Builder UI

Frontend for the Rule Engine (Visual Rule Builder). Vite + React (JavaScript).

## Prerequisites
- Node.js 18+ and npm

## Run
```bash
npm install
npm run dev
```
Opens on http://localhost:5173.

## Backend
The dev server proxies the backend (see `vite.config.js`):
- `/admin/*` and `/api/*` -> `http://localhost:8080`

So API calls use relative paths (e.g. `fetch('/admin/rules')`) and hit the Spring Boot
rule-engine service when it is running - no CORS setup needed in dev.

A thin API client is stubbed in `src/api/ruleEngineApi.js` (list/save/preview rules,
fields, flows, mappings). Integration with the backend comes next.

## Build
```bash
npm run build     # outputs to dist/
npm run preview   # serve the production build locally
```

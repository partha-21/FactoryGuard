# FactoryGuard dashboard (React)

The dashboard you see at http://localhost:3000 is a React app. **You do not need to build it to run
FactoryGuard**: the compiled bundle is already included at
`guard/src/main/resources/static/assets/app.js`, and `./run.sh` just serves it.

Only if you want to change the dashboard:

1. Install Node.js 20 or newer.
2. In this folder run `npm install`, then `npm run build`.
3. The new bundle is written into the `guard` folder. Run `./run.sh` again so the guard image is rebuilt.

Files: `src/App.jsx` (page and data loading), `src/charts.jsx` (sparklines, gauges, line chart),
`src/Pipeline.jsx` (production-line diagram), `src/Tables.jsx` (incidents and deployments),
`src/api.js` (sign-out handling). Styles live in `guard/src/main/resources/static/app.css`.
The sign-in page (`login.html`) is plain HTML.

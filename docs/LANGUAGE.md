# Interface Languages

November 4, 2026 · [Requirements FR-12](SRS.md) · [Verification](VERIFICATION.md)

## Behavior

The login and registration pages also offer a language selector above their tabs. Customers, logistics staff, and administrators can choose 简体中文, 繁體中文, or English from the top-right selector after login. The initial default is Simplified Chinese. Switching updates menus, labels, status descriptions, known system messages, validation rules, and Element Plus controls without reloading the page or clearing forms.

Preferences use browser local storage, keyed by account (`ls_locale:<account>`). The login-page preference uses a separate key (`ls_locale_guest`). On login, an existing account preference takes priority; an account without one inherits and saves the login-page choice. Logout restores the login-page preference. Preferences survive refresh in that browser, but do not synchronize between devices. If storage is unavailable, the current session can still switch language.

Names, addresses, entered goods descriptions, warehouse names, announcements, feedback, and staff notes remain as entered. The default system title is translated; a custom title remains unchanged. API paths, fields, role values, status codes, and stored business values are unchanged. Unknown server messages remain visible in their original form.

## Implementation

- The existing Vue page uses explicit translation bindings, with dictionaries in `src/main/resources/static/logistics-i18n.js`. Translation occurs at display time rather than changing returned records.
- Official Element Plus **2.7.8** locales (`en`, `zh-cn`, `zh-tw`) are served locally, matching the installed component version. Source: `https://cdn.jsdelivr.net/npm/element-plus@2.7.8/dist/locale/<locale>.min.js` (Element Plus, MIT).
- UI translations were produced with DeepL and reviewed for ambiguous terminology and interpolation tokens. The deployed website makes no translation-service calls.
- Two existing template binding defects were found during browser checks: tracking options referenced an undefined `orders` binding, and search suggestions referenced an undefined `searchHits` binding. Both now read the existing reactive state (`s.orderList`, `s.searchHits`).

## Verification

Run the VM regression without additional packages:

```sh
node --test scripts/test-phase-one-frontend.cjs
```

Recorded result: **33 passed** after the login-page extension. Added checks cover all roles, guest preference inheritance, account preference priority, account isolation, unchanged form state and API payloads, translated errors, and preservation of staff notes.

For browser checks, install Playwright in a separate tools directory and make it resolvable by Node (for example, set `NODE_PATH` to that directory's `node_modules`). Microsoft Edge must be installed. No frontend npm build is required.

```sh
node scripts/test-language-browser.cjs
```

Recorded result: **nine role/language combinations passed** in local headless Edge at 1440 × 1000. The script serves local assets and intercepts all API calls with synthetic responses. It checks navigation, English text coverage, interpolation tokens, pagination, form preservation, tracking, feedback and inventory dialogs, administrator settings, and refresh persistence. Screenshots and results are written to the ignored `target/language-browser` directory.

The login-page extension additionally passed guest login/registration switching, retention of username/password fields, refresh persistence, and absence of premature validation errors. Login screenshots were checked at 1440 × 1000 and 800 × 900. These frontend checks do not create accounts or send credentials to a real API.

Maven packaging also succeeded with **37 H2 tests passed**, using JDK 24.0.1 and Maven 3.9.11. The sandboxed attempt stalled during Mockito attachment and was stopped; the completed run used normal local process permissions and the isolated test profile.

These checks are not a rerun of the tester's Postman suite or a complete production workflow. This change does not modify Java code, database schemas, or stored records. Full narrow-screen and cross-browser acceptance remain separate tasks.

## Public Release Check

Application commit `00e577f` became live on Render on November 4, 2026, at 05:05:33 UTC (13:05:33 China Standard Time), deployment `dep-dblbp55bedkc73a4jmqg`. Read-only checks returned health `UP` and HTTP 200 for the page, translation dictionary, and all three component locale files. Their contents matched the local release after normalizing line endings. No production business records were created or modified for these checks. Refresh the public page with Ctrl+F5 if an older version remains cached.

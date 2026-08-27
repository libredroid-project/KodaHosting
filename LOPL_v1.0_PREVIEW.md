# Libre Open Project License (LOPL)

**Version 1.0 PREVIEW**

Copyright (c) 2026 Karol Brzostowski / KodaHosting

SPDX-License-Identifier: LicenseRef-LOPL-1.0-PREVIEW

> **Important Preview Notice:** This is a PREVIEW version of the LOPL v1.0. It is still under development and is being tested in real projects before finalization. The Preview Phase lasts 6 months from the date of first publication. After the Preview Phase ends, the final LOPL v1.0 will be published. Until then, this Preview version may be used in non-commercial projects and small commercial projects with annual revenue under 100,000 EUR. For larger commercial use during the Preview Phase, contact the Licensor for a separate written agreement. Using this Preview version in production at scale is at the user's own legal risk.

> **Not OSI Approved:** This License is NOT approved by the Open Source Initiative (OSI) and is NOT a "free software license" as defined by the Free Software Foundation. The Licensor considers the "Ethical Source" stance to be more important than conforming to definitions that predate the current ecosystem of corporate free-riding on volunteer-maintained infrastructure.

---

## TL;DR

This is the PREVIEW version of the LOPL v1.0, an Ethical Source license. It combines strong copyleft with a tiered commercial reciprocity requirement.

**Status: PREVIEW (6 months).** Not for production at scale yet. Final version comes after the Preview Phase ends.

**You MAY use this Software for free if:**
- You are an individual using it personally
- You are a non-profit, charity, school, university, or research organization
- You are an open-source project that releases derivative works under this License or a compatible strong-copyleft license
- You are a small commercial entity with annual revenue under 100,000 EUR (during the Preview Phase)

**You MUST pay a Commercial License Fee if:**
- You are a commercial entity with annual revenue (including Affiliates) of 100,000 EUR or more
- OR you want to use the Software at production scale during the Preview Phase

**You MUST release all derivative works under this License or a compatible Open Source License.** Copyleft survives commercial license.

**You MUST disclose the server-side source code if you offer the Software as a service.**

This summary is for orientation only. The binding text is below.

---

## Preamble

The Libre Open Project License (LOPL) is a strong-copyleft, dual-licensing framework that combines community-driven open development with mandatory commercial reciprocity. The Licensor believes that open-source communities deserve reciprocal treatment from commercial users, and that massive-scale commercial use of volunteer-maintained infrastructure is a form of exploitation that the existing license ecosystem does not adequately address.

The LOPL is built on three core principles:

1. **Community Freedom.** Individuals, hobbyists, informal groups, and registered non-profits may use, modify, and distribute the Software freely, provided they honor copyleft obligations.

2. **Commercial Reciprocity.** Commercial entities above a small-revenue threshold must pay a tiered license fee based on their ability to pay. The commercial license grants permission for commercial use, but does NOT waive copyleft. All derivative works must still be released under this License or a compatible strong-copyleft license.

3. **Network Reciprocity.** Hosting the Software as a SaaS, web app, or API constitutes distribution. The server-side source code must be disclosed to all interacting users, closing the "ASP loophole" that the Affero GPL addresses.

By downloading, copying, installing, modifying, integrating, distributing, or otherwise using this Software, You explicitly agree to be bound by the terms of this License. If You do not agree to these terms, You must immediately cease all use of the Software and destroy all copies in Your possession or control.

---

## 1. Definitions

**1.1 "Software"** means the original source code, compiled binaries, documentation, configuration files, and any derivative works or modifications thereof, made available under this License by the Licensor.

**1.2 "Licensor"** or **"Copyright Holder"** means the individual(s) or organization(s) that hold the copyright in the Software and offer it under this License.

**1.3 "Licensee"** or **"You"** means any natural person or legal entity exercising rights under this License.

**1.4 "Derivative Work"** means any software, product, or service that:
   (a) incorporates, embeds, statically or dynamically links to, imports, parses, or otherwise relies on the Software to deliver a material portion of its functionality;
   (b) is derived from, based upon, or adapted from the Software; or
   (c) is designed to operate in conjunction with the Software in such a way that the Software provides essential functionality to the Derivative Work.
   Architectural separation, including microservice boundaries, API contracts, network separation, containerization, or service-oriented decomposition, does not by itself exempt a work from being a Derivative Work.

**1.5 "Commercial Entity"** means:
   (a) any legally registered corporation, limited liability company, partnership, or other business entity;
   (b) any government agency, state-owned enterprise, public administration body, or intergovernmental organization;
   (c) any individual acting on behalf of, or funded by, an entity described in (a) or (b);
   (d) any sole proprietor, freelancer, or informal business generating more than 10,000 EUR in gross annual revenue from the activity in which the Software is used.

**1.6 "Non-Commercial Entity"** means:
   (a) individuals acting in a personal capacity without direct or indirect commercial benefit;
   (b) hobbyists and informal study groups;
   (c) officially registered non-profit organizations, charities, foundations, public-interest associations, educational institutions, and research organizations, provided that any use of the Software does not generate commercial revenue for the entity or its members;
   (d) small commercial entities with annual revenue (including Affiliates) of less than 100,000 EUR.

**1.7 "Affiliate"** means, with respect to any entity, any other entity that directly or indirectly controls, is controlled by, or is under common control with such entity. "Control" means the power to direct the management or policies of an entity, whether through ownership of voting securities, by contract, or otherwise.

**1.8 "Commercial Use"** means any use of the Software that is conducted by a Commercial Entity, or by a Non-Commercial Entity in a manner that generates direct or indirect commercial revenue.

**1.9 "Network Use"** means providing access to the functionality of the Software to third parties over a network (e.g., as a SaaS, web application, REST/GraphQL API, microservice, or any similar paradigm).

**1.10 "Contribution"** means any original work of authorship, including but not limited to source code, documentation, design assets, bug reports, or improvement suggestions, intentionally submitted by a Contributor to the Licensor for inclusion in the Software.

**1.11 "Contributor"** means any natural or legal person who submits a Contribution to the Software.

**1.12 "Patent Claims"** means any patent claim(s) that would be infringed by the making, using, selling, offering for sale, importing, or transferring of the Software, but only to the extent such claims are owned or controlled by the Licensor or Contributor and can be licensed without violating the rights of third parties.

**1.13 "Annual Revenue"** means, with respect to a Commercial Entity and its Affiliates taken together, the total gross revenue recognized under generally accepted accounting principles for the most recently completed fiscal year.

**1.14 "Open Source License"** means any license that provides copyleft at least as strong as this License, including but not limited to the GNU GPL v3 or later, the GNU AGPL v3 or later, the Mozilla Public License v2 or later, the European Union Public License v1.2 or later, or this License.

**1.15 "Preview Phase"** means the 6-month period starting from the date of first publication of this Preview version by the Licensor, during which this Preview version is being tested and may be used subject to the restrictions in Section 4.7.

**1.16 "Production at Scale"** means any use of the Software in a commercial product or service that is actively offered to more than 100 end users, or that generates more than 100,000 EUR in annual revenue, or that is operated by a Commercial Entity with Annual Revenue of 100,000 EUR or more.

---

## 2. Grant of Copyright License (The "Libre" Clause)

**2.1 Grant to Non-Commercial Entities.** Subject to the restrictions in Sections 3, 4, 5, and 6, the Licensor grants to Non-Commercial Entities a worldwide, non-exclusive, royalty-free, non-transferable license to use, reproduce, modify, distribute, and display the Software.

**2.2 Strict Viral Copyleft.** Any Derivative Work created by exercising rights under this License MUST be released in its entirety under this License (LOPL) or under an Open Source License that provides copyleft at least as strong as this License. Specifically:
   (a) The complete corresponding source code of the Derivative Work must be made available to all recipients, in a form suitable for modification and redistribution.
   (b) No additional restrictions may be imposed on recipients beyond those imposed by this License.
   (c) **No "Wrapper" Loopholes:** You may not hide proprietary, closed-source code behind an open-source "wrapper," microservice, API boundary, or any other architectural device. If your closed-source application relies on this Software to deliver its core functionality, your entire application is deemed a Derivative Work and must be released under this License or a compatible Open Source License.
   (d) **Combined Works:** When the Software is combined with other work in a single distribution, the combination must be licensed as a whole under a license that satisfies this Section 2.2.

**2.3 SaaS / Network Distribution Clause.** Network Use constitutes distribution for the purposes of this License.
   (a) If You host this Software on a server and allow users to interact with it via an app, website, or API, You MUST make the complete corresponding source code of Your server-side implementation publicly available to all interacting users under this License or an Open Source License at least as strong.
   (b) The source code must be offered through a publicly accessible, durable, version-controlled repository without authentication barriers, paywalls, or non-disclosure requirements.
   (c) The source code must remain available for at least twelve (12) months after You cease offering the hosted service.

**2.4 Anti-Circumvention.** You may not impose technical measures on the Software or any Derivative Work that would prevent recipients from exercising the rights granted under this License, including DRM, hardware locks, encryption keys that are not publicly disclosed, or any analogous technological protection measures.

**2.5 Attribution.** You must retain all copyright, attribution, and license notices in the Software and in any Derivative Work. You must provide reasonable attribution to the original Licensor in any documentation, about-page, or equivalent notice typically shown to end users.

---

## 3. Patent License

**3.1 Grant of Patent License.** Subject to the terms and conditions of this License, each Contributor hereby grants to You a perpetual, worldwide, non-exclusive, royalty-free, non-transferable license, with no right to sublicense, under Patent Claims of such Contributor to make, use, sell, offer for sale, import, and otherwise transfer the Software.

**3.2 Patent Retaliation.** If You or any of Your Affiliates institute patent litigation against any entity (including a counterclaim in a lawsuit), alleging that the Software or a Contribution incorporated into the Software constitutes direct or contributory patent infringement, then any patent license granted to You under this License for the Software shall terminate as of the date such litigation is filed.

---

## 4. Commercial Use Restrictions (The Enterprise Clause)

**4.1 Commercial License Required.** The royalty-free grant in Section 2 is strictly void for Commercial Entities with annual revenue (including Affiliates) of 100,000 EUR or more. Commercial Use of the Software without a valid Commercial License is willful copyright infringement.

**4.2 Tiered Commercial License Fee.** Commercial Entities must obtain a Commercial License before any Commercial Use. The Commercial License fee is determined by the Licensee's Annual Revenue and applies per overarching project or product:

| Tier | Annual Revenue (Licensee + Affiliates) | Commercial License Fee (EUR) |
|------|----------------------------------------|------------------------------|
| Tier 1 (Micro) | Less than 100,000 EUR | Free (covered by Section 2) |
| Tier 2 (Small) | 100,000 EUR to less than 1,000,000 EUR | 1,000 EUR |
| Tier 3 (Mid-Size) | 1,000,000 EUR to less than 10,000,000 EUR | 5,000 EUR |
| Tier 4 (Large) | 10,000,000 EUR to less than 100,000,000 EUR | 10,000 EUR |
| Tier 5 (Global) | 100,000,000 EUR or more | 50,000 EUR |

All fees are stated in Euro (EUR), exclusive of applicable VAT. Fees are payable in advance, prior to commencement of Commercial Use. The fee is a one-time, perpetual license fee per overarching project or product.

**4.3 Copyleft Survives Commercial License.** A Commercial License grants the right to engage in Commercial Use of the Software, but does NOT waive the copyleft obligations of Section 2.2. Commercial Licensees MUST still release all Derivative Works of the Software under this License or a compatible Open Source License. The Commercial License does not permit closed-source derivatives.

**4.4 Contractor Loophole Closed.** A Commercial Entity may not circumvent the restrictions of this Section 4 by hiring independent contractors, freelancers, consultants, or any other person to integrate, host, or distribute the Software on its behalf. The Commercial Entity that ultimately benefits from the use of the Software is liable.

**4.5 Affiliate Loophole Closed.** The restrictions of this Section 4 apply to the Commercial Entity and all of its Affiliates as a single combined entity. The Annual Revenue and Commercial Use of all Affiliates are aggregated for determining the tier.

**4.6 Internal Use.** For the avoidance of doubt, internal use of the Software by a Commercial Entity for its own business operations constitutes Commercial Use and requires a Commercial License, except as provided in Section 4.2 Tier 1.

**4.7 Preview Phase Restrictions.** During the Preview Phase, the following additional restrictions apply:
   (a) Production at Scale is NOT permitted under this Preview version of the License. Commercial Entities that wish to use the Software at Production at Scale during the Preview Phase must contact the Licensor and obtain a separate written agreement.
   (b) For non-commercial use and small commercial use (under 100,000 EUR annual revenue), this Preview version is fully usable and binding, but the Licensee acknowledges that the License is still being tested and may be revised before the final version is published.
   (c) Use of this Preview version in any context is at the Licensee's own legal risk. The Licensor makes no representation that this Preview version is suitable for any particular purpose, that it will be enforced as written, or that it will not be modified before finalization.
   (d) The Licensor may, at any time during the Preview Phase, publish a revised Preview version or the final LOPL v1.0. Licensees who have adopted this Preview version are encouraged, but not required, to migrate to the final version when it is published.
   (e) Contributions submitted during the Preview Phase will be governed by the Contributor License in Section 5 and will continue to be governed by the final version of LOPL v1.0 when published.

---

## 5. Contributor License

**5.1 Inbound equals Outbound.** By submitting a Contribution to the Software, You hereby grant to the Licensor and to all recipients of the Software a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, reproduce, modify, distribute, display, and sublicense the Contribution under the terms of this License.

**5.2 Copyright Retention.** You retain ownership of the copyright in Your Contribution. The license granted in Section 5.1 is a license, not an assignment.

**5.3 Patent License from Contributors.** You hereby grant to the Licensor and to all recipients of the Software a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license under Patent Claims of Yours that are necessary to make, use, sell, offer for sale, import, and otherwise transfer Your Contribution.

**5.4 Representation of Authority.** You represent that You are legally entitled to grant the licenses in Sections 5.1 and 5.3. If Your employer has rights to Your Contribution, You represent that You have received permission to make the Contribution on behalf of that employer.

---

## 6. Trademarks

**6.1 No Trademark License.** This License does not grant any rights to use any Trademarks of the Licensor, except as strictly required to comply with the attribution requirements of Section 2.5.

**6.2 No Implied Endorsement.** You may not use any Trademark of the Licensor to endorse or promote products derived from the Software without prior written permission from the Licensor.

**6.3 Forking.** If You create a fork of the Software, You must choose a new project name that is not confusingly similar to the original. The Licensor retains all rights in the original project name and Trademarks.

---

## 7. Auditing and Enforcement

**7.1 Audit Right.** The Licensor reserves the right to request reasonable proof of compliance from any entity exercising rights under this License.

**7.2 Confidentiality of Audit.** The Licensor shall keep confidential any non-public information disclosed by the Licensee during an audit, except to the extent required to enforce this License or as required by law.

**7.3 Consequences of Non-Compliance.**
   (a) If an entity refuses to provide evidence required under Section 7.1, or obscures or misrepresents its compliance metrics, the Licensor may revoke the License immediately upon written notice.
   (b) Continued use of the Software after License revocation carries statutory damages under applicable copyright law, in addition to the Commercial License Fee that would have applied under Section 4.2.

**7.4 No Waiver.** Failure by the Licensor to enforce any provision of this License on any occasion does not waive the right to enforce the same or any other provision on any other occasion.

**7.5 Preview Phase Limitation.** During the Preview Phase, the Licensor will not initiate enforcement actions against Licensees for non-compliance with this Preview version, except in cases of willful or repeated violations. The Licensor may, however, revoke the License of any Licensee that breaches the Preview restrictions in Section 4.7.

---

## 8. Termination

**8.1 Automatic Termination.** This License terminates automatically and immediately upon any breach of Sections 2.2 (copyleft), 2.3 (SaaS disclosure), 3.2 (patent retaliation), 4 (commercial restrictions), or 4.7 (Preview restrictions).

**8.2 Termination for Non-Payment.** The Licensor may terminate this License upon 30 days' written notice if any fee required under Section 4 remains unpaid past its grace period.

**8.3 Survival of Copyleft.** Termination of this License does not terminate the rights of downstream recipients who received Derivative Works under a valid grant.

**8.4 Destruction on Termination.** Upon termination, You must immediately cease all use of the Software and destroy all copies in Your possession or control, except for Derivative Works that You have already validly distributed to third parties.

---

## 9. Limitation of Liability and Warranty

**9.1 No Warranty.** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

**9.2 Limitation of Liability.** IN NO EVENT SHALL THE AUTHORS, COPYRIGHT HOLDERS, LICENSOR, OR CONTRIBUTORS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

**9.3 Preview Phase Risk.** During the Preview Phase, the Licensee explicitly acknowledges that the License is still being tested and may not be legally enforceable as written. The Licensee uses this Preview version at their own legal risk.

**9.4 Carve-Out for Paid Commercial Licenses.** Where a Commercial Licensee has paid a Commercial License Fee under Section 4.2, the aggregate liability of the Licensor to that Commercial Licensee for direct damages arising out of this License shall not exceed the amount of the Commercial License Fee actually paid.

---

## 10. General Provisions

**10.1 Choice of Law.** This License is governed by the laws of the **Federal Republic of Germany**, to the exclusion of the UN Convention on Contracts for the International Sale of Goods (CISG).

**10.2 Jurisdiction.** The exclusive place of jurisdiction for all disputes arising out of or in connection with this License shall be the courts of **Berlin, Germany**, provided that the Licensor is domiciled in Germany.

**10.3 Severability.** If any provision of this License is held to be invalid, illegal, or unenforceable, that provision shall be modified to the minimum extent necessary to make it valid and enforceable, and the remaining provisions shall remain in full force and effect.

**10.4 Modifications and Versions.** The Licensor may publish revised and/or new versions of this License from time to time. Each version will be given a distinguishing version number. The Software is licensed under the version of this License specified in the Software's NOTICE file. During the Preview Phase, the Licensor may publish revised Preview versions, and finally the LOPL v1.0 (final).

**10.5 No Waiver.** No failure or delay by the Licensor in exercising any right under this License shall operate as a waiver thereof.

**10.6 Export Compliance.** You agree to comply with all applicable export control laws and regulations of Germany, the European Union, and other jurisdictions.

**10.7 Data Protection.** Where the Software processes personal data within the meaning of the EU General Data Protection Regulation (GDPR), the Licensee is solely responsible for compliance with applicable data protection laws.

---

## Appendix A: How to Apply This License

To apply this License to Your software:

1. **Include the License file.** Place the complete text of this License (named `LICENSE` or `LOPL-LICENSE`) in the root directory of Your project.

2. **Add copyright and permission notice to each source file:**
   ```
   Copyright (c) 2026 Karol Brzostowski / KodaHosting

   Licensed under the Libre Open Project License (LOPL) Version 1.0 PREVIEW.
   See LICENSE file for full terms.
   For commercial use or production at scale, contact: support@host.kodanetwork.eu
   ```

3. **Add a NOTICE file** in the root directory containing:
   - The project name and short description
   - The copyright line: `Copyright (c) 2026 Karol Brzostowski / KodaHosting`
   - The license identifier: `LicenseRef-LOPL-1.0-PREVIEW`
   - Contact information for commercial license inquiries

4. **Update Your manifest:**
   ```
   "license": "LicenseRef-LOPL-1.0-PREVIEW"
   ```

5. **Provide a Commercial License Agreement** (typically at `COMMERCIAL-LICENSE.md` in Your project root).

6. **Provide a Contributor License Agreement** (typically at `CLA.md` in Your project root).

---

## Appendix B: Zusammenfassung auf Deutsch

**LOPL v1.0 PREVIEW Kurzfassung**

*Hinweis: Diese deutsche Zusammenfassung dient der Orientierung. Im Konfliktfall gilt der englische Haupttext.*

Die LOPL ist eine strikte, viral-copyleft "Ethical Source"-Lizenz. Diese Version ist die PREVIEW-Version, die für 6 Monate getestet wird, bevor die finale LOPL v1.0 veröffentlicht wird.

**Status: PREVIEW**

- Die PREVIEW-Phase dauert 6 Monate ab Erstveröffentlichung
- Während der PREVIEW-Phase darf die Lizenz in nicht-kommerziellen Projekten und in kleinen kommerziellen Projekten (unter 100.000 EUR Jahresumsatz) genutzt werden
- Für kommerzielle Nutzung in größerem Maßstab (Production at Scale) benötigen Sie während der PREVIEW-Phase eine separate schriftliche Vereinbarung mit dem Lizenzgeber
- Die Nutzung erfolgt auf eigenes rechtliches Risiko, da die Lizenz noch nicht final getestet ist
- Die finale LOPL v1.0 wird nach Ablauf der PREVIEW-Phase veröffentlicht

**Erlaubt (kostenlos):**
- Privatpersonen, Hobbyisten
- Eingetragene Non-Profit-Organisationen, Schulen, Universitäten
- Open-Source-Projekte, die ihre Derivate unter LOPL oder einer kompatiblen Copyleft-Lizenz veröffentlichen
- Kleine gewerbliche Nutzer mit Jahresumsatz unter 100.000 EUR

**Kommerzielle Lizenz erforderlich (gestaffelt):**
| Stufe | Jahresumsatz (inkl. verbundene Unternehmen) | Gebühr |
|------|----------------------------------------------|--------|
| Tier 1 (Micro) | Unter 100.000 EUR | Kostenlos |
| Tier 2 (Klein) | 100.000 EUR bis 1 Mio. EUR | 1.000 EUR |
| Tier 3 (Mittelstand) | 1 Mio. bis 10 Mio. EUR | 5.000 EUR |
| Tier 4 (Groß) | 10 Mio. bis 100 Mio. EUR | 10.000 EUR |
| Tier 5 (Global) | 100 Mio. EUR oder mehr | 50.000 EUR |

**Pflichten:**
- Vollständige Quellcode-Offenlegung aller Derivate unter LOPL oder kompatibler Copyleft-Lizenz (Copyleft überlebt auch kommerzielle Lizenz)
- SaaS-Pflicht: Vollständiger serverseitiger Code in öffentlichem Versionskontrollsystem
- Audit-Mitwirkung bei Nachweisaufforderung
- Patentrechtsabtretung für Mitwirkende
- Markenschutz: Keine Nutzung von Name/Logo ohne Erlaubnis

**Recht:** Deutsches Recht, Gerichtsstand Berlin

**Nicht OSI-zugelassen:** Diese Lizenz ist eine Ethical-Source-Lizenz, die kommerzielle Nutzung an Bedingungen knüpft, die über das reine Urheberrecht hinausgehen.

---

## Appendix C: Changelog

### Version 1.0 PREVIEW (current)
- Initial release of the Preview version.
- Strict viral copyleft with SaaS/Network Use disclosure.
- Tiered commercial license fees (0 EUR to 50,000 EUR) based on Annual Revenue.
- Copyleft survives commercial license (commercial licensees must still release derivative works under LOPL or compatible license).
- Patent License and Patent Retaliation.
- Contributor License (inbound = outbound).
- Trademark protection.
- Audit rights with confidentiality.
- Choice of law: Germany, Berlin jurisdiction.
- Preview Phase: 6 months, with restrictions on Production at Scale.
- Risk acknowledgment: Licensees use at their own legal risk during Preview Phase.

### Version 1.0 (planned, after Preview Phase)
- Final version with revisions based on Preview Phase feedback.
- Removal of Preview Phase restrictions.
- Potentially: revised tier thresholds based on feedback.
- Potentially: additional compatibility clauses based on legal review.

---

*End of License*

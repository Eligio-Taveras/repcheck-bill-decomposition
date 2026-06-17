# DP-0 gold review — Claude-judged concept groupings

For each bill: a summary, then each concept group with its sections described. Skim to confirm the groupings make sense. To correct one, edit `evaluation/src/main/resources/gold/<versionId>.json` and set `labelStatus` to `reviewed-groups`.

## s 177012 — 1 sections → 1 groups (parser: Fallback)

**Summary:** This bill sets the specific start time for the opening session of the 105th Congress to noon on January 7, 1997. It is a simple procedural measure governing the convening of Congress.

### Congressional Session Start
- **[0] Fallback** — This bill sets the start time of the first session of the 105th Congress to noon on January 7, 1997.

## hjres 402717 — 2 sections → 1 groups (parser: GpoText)

**Summary:** This bill is a joint resolution from the 104th Congress designed to provide continuing appropriations for the federal government for fiscal year 1996. It establishes the formal legislative basis for keeping government funding in place on a temporary basis. The resolution is adopted by both chambers of Congress acting together.

### Continuing Appropriations Resolution
- **[0] Fallback** — Introduces a joint resolution in the 104th Congress to provide further continuing appropriations for fiscal year 1996.
- **[1] Section** — States that the resolution is formally adopted by both the Senate and the House of Representatives assembled in Congress.

## sjres 373803 — 2 sections → 1 groups (parser: GpoText)

**Summary:** This is a joint resolution from the 107th Congress that sets the date for convening the first session of the 108th Congress. It was passed by both the Senate and the House on November 14, 2002.

### Congressional Session Convening
- **[0] Fallback** — This is a joint resolution from the 107th Congress establishing the date for convening the first session of the 108th Congress.
- **[1] Sec. 2002** — This section records that the resolution was considered and passed by both the Senate and the House on November 14, 2002.

## sjres 408763 — 3 sections → 2 groups (parser: GpoText)

**Summary:** This Senate Joint Resolution grants congressional consent for six New England states to form the Northeast Interstate Dairy Compact, which allows the member states to jointly regulate fluid milk prices above federal order levels. The bill sets specific conditions on how the compact operates, including limits on manufacturing milk price regulation, rules on additional state membership, and overproduction safeguards. Congress also reserves the right to amend or repeal the consent and requires the Compact Commission to repay the federal government for any excess dairy purchase costs caused by above-average milk production growth in the region.

### Congressional Consent and Introduction
- **[0] Fallback** — This is the introductory header of a Senate Joint Resolution introduced in the 104th Congress to grant congressional consent to the Northeast Interstate Dairy Compact among six New England states.
- **[1] Sec. 1** — Congress grants conditional consent to the Northeast Interstate Dairy Compact, imposing restrictions on manufacturing milk price regulation, limiting which additional states may join, and establishing rules for over-order pricing, producer payments, overproduction prevention, and pooling mechanisms.

### Federal Oversight and Reimbursement
- **[2] Sec. 2** — Congress reserves the right to alter or repeal the act and requires the Compact Commission to reimburse the Commodity Credit Corporation for any increased dairy purchase costs resulting from fluid milk production growth within the Compact region that exceeds the national average.

## hconres 208332 — 2 sections → 2 groups (parser: GpoText)

**Summary:** This bill is a Senate amendment to a House concurrent resolution (H. Con. Res. 38) from the 110th Congress. The sole substantive change is rescheduling the joint session of Congress to receive the President's message, moving it from Wednesday to Tuesday.

### Document Header
- **[0] Fallback** — This is the header identifying the document as an engrossed Senate amendment to H. Con. Res. 38 during the 110th Congress, received January 22, 2007.

### Joint Session Reschedule
- **[1] Section** — The Senate amends the House concurrent resolution by changing the day of the joint session of Congress to receive the President's message from Wednesday to Tuesday.

## hconres 357076 — 40 sections → 7 groups (parser: GpoText)

**Summary:** This is the House-passed concurrent budget resolution for FY2008 (covering FY2009–2012), replacing the FY2007 resolution. It sets overall federal revenue, spending, and deficit targets by budget category, establishes a series of adjustment mechanisms allowing the Budget Committee chairman to accommodate specific legislative priorities without increasing the deficit, and lays out enforcement rules and policy statements on issues ranging from tax relief and veterans' care to homeland security and entitlement reform.

### Resolution Framework
- **[0] Fallback** — This is the engrossed concurrent resolution on the budget passed by the House during the 110th Congress, 1st Session.
- **[1] Sec. 1** — This section declares that this resolution replaces the FY2007 budget resolution and establishes it as the concurrent resolution on the budget for FY2008, covering fiscal years 2009 through 2012, and provides a table of contents.

### Overall Budget Levels
- **[2] Sec. 101** — This section sets the specific recommended dollar amounts for federal revenues, new budget authority, total outlays, deficits, and debt levels for fiscal years 2007 through 2012.
- **[3] Sec. 102** — This section breaks down the appropriate levels of new budget authority and outlays for each major spending category (such as defense, international affairs, and energy) for fiscal years 2007 through 2012.

### Chairman Adjustment Allowances
- **[4] Sec. 201** — This section allows the House Budget Committee chairman to adjust spending limits to accommodate legislation expanding the State Children's Health Insurance Program (SCHIP), provided the legislation does not increase the deficit over the relevant budget periods.
- **[5] Sec. 202** — This section allows the House Budget Committee chairman to adjust spending limits to accommodate legislation that reduces the Alternative Minimum Tax burden on middle-income families, as long as the legislation does not increase the deficit.
- **[6] Sec. 203** — This section allows the House Budget Committee chairman to adjust spending limits for legislation providing various middle-income tax relief measures, such as extending the child tax credit and marriage penalty relief, provided the legislation does not increase the deficit.
- **[7] Sec. 204** — This section allows the House Budget Committee chairman to adjust spending limits for legislation reauthorizing farm programs under the 2002 Farm Bill by up to $20 billion, provided the legislation does not increase the deficit.
- **[8] Sec. 205** — This section allows the House Budget Committee chairman to adjust spending limits for legislation that makes college more affordable through reforms to the Higher Education Act, provided the legislation does not increase the deficit.
- **[9] Sec. 206** — This section allows the House Budget Committee chairman to adjust spending limits for legislation that improves Medicare benefits and physician reimbursement rates, provided the legislation does not increase the deficit.
- **[10] Sec. 207** — This section allows the House Budget Committee chairman to adjust spending limits for legislation advancing clean energy goals consistent with H.R. 6, the Clean Energy Act of 2007, provided the legislation does not increase the deficit.
- **[11] Sec. 208** — This section allows the House Budget Committee chairman to adjust spending limits for legislation creating an affordable housing fund offset by reforms to government-sponsored enterprise regulation, provided the legislation does not increase the deficit.
- **[12] Sec. 209** — This section allows the House Budget Committee chairman to adjust spending limits for legislation that provides or increases benefits for Filipino veterans of World War II and their survivors, provided the legislation does not increase the deficit.
- **[13] Sec. 210** — This section allows the House Budget Committee chairman to adjust spending limits for legislation reauthorizing the Secure Rural Schools and Community Self-Determination Act, provided the legislation does not increase the deficit.
- **[14] Sec. 211** — This section allows the House Budget Committee chairman to adjust spending limits for legislation prohibiting the Bonneville Power Administration from making early payments on its federal bond debt, provided the legislation does not increase the deficit.
- **[15] Sec. 212** — Allows the House Budget Committee chairman to adjust spending allocations to accommodate legislation extending the Transitional Medical Assistance program through fiscal year 2008, provided the legislation does not increase the deficit over specified multi-year periods.

### Appropriations Enforcement Rules
- **[16] Sec. 301** — Permits the House Appropriations Committee's spending allocation to be increased when appropriations bills designate additional funds for specific program integrity efforts—including Social Security disability reviews, IRS tax compliance, healthcare fraud prevention, and unemployment insurance improper payment reviews—that are expected to generate offsetting savings.
- **[17] Sec. 302** — Generally prohibits advance appropriations in House spending bills, but allows up to $25.558 billion in advance budget authority for fiscal years 2009 and 2010 for a specific list of programs identified in the resolution's joint explanatory statement.
- **[18] Sec. 303** — Exempts from standard congressional budget enforcement rules any House spending for overseas military deployments and related activities for fiscal years 2008–2009, as well as any nondefense discretionary spending designated as meeting emergency needs.
- **[19] Sec. 304** — Specifies that budget allocation adjustments made under this resolution apply during consideration of relevant legislation, take effect upon enactment, must be published in the Congressional Record, and are determined by the Budget Committee.
- **[20] Sec. 305** — Directs the Budget Committee chairman to update the resolution's spending levels and allocations whenever a law changes the budgetary concepts or definitions used to measure spending, in accordance with the Balanced Budget and Emergency Deficit Control Act.
- **[21] Sec. 306** — Requires that budget conference reports include Social Security Administration discretionary administrative expenses within the Appropriations Committee's spending allocation, and that such amounts be counted when enforcing spending limits in the House.
- **[22] Sec. 307** — Establishes that the budget enforcement provisions in this title are adopted as formal rules of the House, superseding inconsistent rules, while preserving the House's constitutional right to change its rules at any time.

### Tax and Defense Policy
- **[23] Sec. 401** — States the resolution's policy of providing middle-income tax relief—including AMT reform, child tax credits, marriage penalty relief, estate tax changes, and other measures—on a revenue-neutral basis offset by fairer and more efficient tax reforms.
- **[24] Sec. 402** — Expresses the resolution's defense policy priorities, including funding nuclear nonproliferation, maintaining TRICARE fees, increasing military pay and benefits, moderating missile defense and satellite spending, improving Pentagon financial management, and directing resulting savings toward higher-priority defense needs and military healthcare improvements.
- **[25] Sec. 403** — States that the reconciliation instructions to the Education and Labor Committee must not be interpreted to reduce college affordability assistance, including aid programs run by nonprofit state agencies.

### Sense of House Statements
- **[26] Sec. 501** — Expresses the sense of the House that the resolution provides significantly increased veterans' health care and benefits funding above both the President's request and baseline levels, rejects proposed fee increases, and adds resources for mental health, brain injury treatment, and disability claims processing.
- **[27] Sec. 502** — Expresses the sense of the House that the resolution invests $450 million above the President's request in science, technology, and energy functions to keep America competitive by funding scientific research, education of new scientists and engineers, and development of clean energy technologies.
- **[28] Sec. 503** — Expresses the sense of the House that the resolution provides homeland security funding above the President's requested levels across multiple budget functions to strengthen transportation security, port scanning, border patrol, first responder equipment, and public health preparedness.
- **[29] Sec. 504** — Expresses the sense of the House that Gulf Coast recovery needs must be addressed without delay, additional oversight of recovery contracting is needed, and the resolution provides new funding mechanisms and FEMA appropriations to support ongoing Hurricane Katrina and Rita relief efforts.
- **[30] Sec. 505** — Expresses the House's view that growing entitlement costs should be addressed by reducing the deficit, paying down debt, and controlling broader health care cost growth while protecting beneficiaries.
- **[31] Sec. 506** — Expresses the House's view that existing USDA hunger-fighting programs, especially food stamps, should be maintained and expanded to better reach food-insecure Americans.
- **[32] Sec. 507** — Expresses the House's view that pay-as-you-go legislation should be passed to make health insurance more affordable and accessible, with special attention to small businesses and health information technology.
- **[33] Sec. 508** — Expresses the House's view that Congress should restore the statutory pay-as-you-go budget rule in its original form from the Budget Enforcement Act of 1990 in order to reduce the deficit.
- **[34] Sec. 509** — Expresses Congress's view that the federal budget process should take into account the government's Financial Report, including its net operating cost, financial position, and long-term liabilities.
- **[35] Sec. 510** — Expresses the House's view that civilian federal employees should receive pay raises at the same time and in the same amount as members of the uniformed military services.
- **[36] Sec. 511** — Expresses the House's view that all committees should review programs under their jurisdiction to identify and eliminate wasteful and fraudulent spending, highlighting specific funding adjustments and oversight requirements included elsewhere in the resolution.
- **[37] Sec. 512** — Expresses the House's view that additional legislation is needed to ensure states have sufficient resources to collect owed child support and pass 100 percent of collected payments directly to families.
- **[38] Sec. 513** — Expresses the House's view that the federal government should cover burial plot allowances for eligible spouses and children of veterans interred in state veterans cemeteries, consistent with pay-as-you-go budget rules.

### Reconciliation Instructions
- **[39] Sec. 601** — Instructs the House Committee on Education and Labor to identify and report legislative changes that reduce the deficit by $75 million over fiscal years 2007 through 2012, with recommendations due by September 10, 2007.

## sconres 267921 — 2 sections → 1 groups (parser: GpoText)

**Summary:** This bill is a Senate Concurrent Resolution (S. Con. Res. 46) from the 108th Congress that makes a technical correction to a previously passed piece of legislation, H.R. 1298. Specifically, it changes the designated official responsible for correcting the enrollment of H.R. 1298 from the Secretary of the Senate to the Clerk of the House of Representatives.

### Legislative Enrollment Correction
- **[0] Fallback** — This is the legislative header identifying the bill as Senate Concurrent Resolution 46 in its House-amended form during the 108th Congress, passed by the House on May 21, 2003.
- **[1] Section** — The House amends S. Con. Res. 46 by replacing the reference to the 'Secretary of the Senate' with the 'Clerk of the House of Representatives' as the official responsible for correcting the enrollment of H.R. 1298.

## sconres 356661 — 40 sections → 10 groups (parser: GpoText)

**Summary:** This bill is the concurrent budget resolution for fiscal year 2008, setting overall federal spending, revenue, and deficit targets for fiscal years 2007–2012 across all major budget categories. It includes numerous reserve funds allowing flexible adjustments for specific legislative priorities (such as SCHIP, AMT relief, farm programs, and veterans benefits) provided they do not increase the deficit. The resolution also establishes House budget enforcement rules, states various policy positions on defense, veterans, science, homeland security, and entitlements, and directs one committee to produce deficit-reducing reconciliation legislation.

### Enactment and Overview
- **[0] Fallback** — The House amends the Senate's concurrent budget resolution by striking all content after the enacting clause and replacing it with new text.
- **[1] Sec. 1** — This section declares the resolution to be the official congressional budget for fiscal year 2008 and provides a table of contents for the entire document.

### Overall Budget Levels
- **[2] Sec. 101** — This section sets the specific recommended dollar amounts for federal revenues, new budget authority, total outlays, deficits, and debt levels for fiscal years 2007 through 2012.
- **[3] Sec. 102** — This section specifies the approved levels of new budget authority and outlays for each major federal spending category (such as defense, international affairs, energy, and science) for fiscal years 2007 through 2012.

### Policy-Specific Reserve Funds
- **[4] Sec. 201** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation expanding the State Children's Health Insurance Program (SCHIP), provided the changes do not increase the deficit.
- **[5] Sec. 202** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation reducing the alternative minimum tax burden on middle-income families, provided the changes do not increase the deficit.
- **[6] Sec. 203** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation providing various tax relief measures for middle-income families, provided the changes do not increase the deficit.
- **[7] Sec. 204** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation reauthorizing federal farm programs with up to $20 billion in new budget authority, provided the changes do not increase the deficit.
- **[8] Sec. 205** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation making college more affordable through reforms to the Higher Education Act, provided the changes do not increase the deficit.
- **[9] Sec. 206** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation improving Medicare for beneficiaries, such as increasing physician reimbursement rates and improving the prescription drug benefit, provided the changes do not increase the deficit.
- **[10] Sec. 207** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation furthering the clean energy goals of H.R. 6, provided the changes do not increase the deficit and any appropriations adjustments do not exceed projected revenues from that bill.
- **[11] Sec. 208** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation creating an affordable housing fund offset by reforming government-sponsored enterprises, provided the changes do not increase the deficit.
- **[12] Sec. 209** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation providing or increasing benefits for Filipino World War II veterans and their survivors, provided the changes do not increase the deficit.
- **[13] Sec. 210** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation reauthorizing the Secure Rural Schools and Community Self-Determination Act, provided the changes do not increase the deficit.
- **[14] Sec. 211** — This section allows the Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation prohibiting the Bonneville Power Administration from making early debt repayments to the Treasury, provided the changes do not increase the deficit.
- **[15] Sec. 212** — Allows the House Budget Committee chairman to adjust spending allocations and budget totals to accommodate legislation extending the Transitional Medical Assistance program through fiscal year 2008, provided the legislation does not increase the deficit over specified multi-year periods.

### Appropriations Enforcement Rules
- **[16] Sec. 301** — Permits the House Appropriations Committee's spending allocation to be increased for fiscal year 2008 if appropriations bills designate additional funding for specific program integrity activities—Social Security disability reviews, IRS tax compliance, healthcare fraud control, and unemployment insurance improper payment reviews—that are expected to generate savings.
- **[17] Sec. 302** — Prohibits advance appropriations in general House appropriations bills except for a specified list of programs, which may receive up to $25.558 billion in advance budget authority for fiscal years 2009 or 2010.
- **[18] Sec. 303** — Exempts from standard congressional budget enforcement rules any fiscal year 2008 or 2009 appropriations that are designated for overseas military deployments and related activities, or that are designated as emergency nondefense discretionary spending.
- **[21] Sec. 306** — Requires that the Social Security Administration's discretionary administrative expenses be included in the Appropriations Committee's budget allocation and counted when measuring compliance with spending limits, notwithstanding rules that would otherwise exclude them.

### Budget Process Administration
- **[19] Sec. 304** — Establishes that any spending allocation or aggregate adjustments made under this resolution apply during consideration of the relevant measure, take effect upon its enactment, must be published in the Congressional Record, and are treated as if they were part of the original resolution, with the Budget Committee making all official budget level determinations.
- **[20] Sec. 305** — Requires the Budget Committee chairman to update the resolution's budget levels and allocations to reflect any congressionally enacted changes in budgetary concepts or definitions, following existing law procedures.
- **[22] Sec. 307** — Formally adopts the budget enforcement provisions of this title as official rules of the House, clarifying that they supersede inconsistent House rules but can be changed by the House at any time like any other rule.

### Tax and AMT Policy Statements
- **[23] Sec. 401** — States the resolution's policy of relieving middle-income families from the Alternative Minimum Tax and extending various middle-class tax benefits, with the costs offset by tax code reforms that improve fairness, efficiency, compliance, and simplicity.

### Defense and Military Policy
- **[24] Sec. 402** — States the resolution's defense policy priorities, including fully funding nuclear nonproliferation programs, holding TRICARE fees steady for military retirees, improving military pay and benefits, reducing missile defense and satellite spending to more prudent levels, and directing resulting savings toward higher-priority defense needs and military healthcare improvements.

### Higher Education Policy
- **[25] Sec. 403** — Declares that the reconciliation instructions to the Education and Labor Committee must not be interpreted to cut student financial aid or assistance provided through nonprofit state agencies that help make college more affordable.

### Non-Binding Policy Statements
- **[26] Sec. 501** — Expresses the House's view that the resolution substantially increases veterans' health care and benefits funding above both the President's request and prior-year levels, rejects proposed fee increases for veterans, and provides additional resources for mental health, brain injury treatment, and disability claims processing.
- **[27] Sec. 502** — Expresses the House's commitment to funding science, technology, and education above the President's requested levels in order to maintain U.S. global competitiveness, support the training of new scientists and engineers, and work toward doubling funding for basic research and clean energy development.
- **[28] Sec. 503** — Expresses the House's view that the resolution provides homeland security funding above the President's requested levels across multiple budget functions in order to strengthen port security, cargo scanning, border patrol, first responder equipment, and public health preparedness.
- **[29] Sec. 504** — Expresses the House's view that Gulf Coast recovery needs from Hurricanes Katrina and Rita must be addressed promptly, that the resolution provides a reserve fund for affordable housing and additional recovery funding, and that stronger oversight and contracting reform are needed for disaster recovery efforts.
- **[30] Sec. 505** — Expresses the House's view that growing entitlement costs should be addressed by reducing the deficit, paying down debt, controlling broader health care cost growth, and protecting beneficiaries without burdening future generations.
- **[31] Sec. 506** — Expresses the House's view that federal nutrition programs, especially food stamps, should be maintained and expanded to better reach the tens of millions of food-insecure Americans.
- **[32] Sec. 507** — Expresses the House's view that legislation should be passed under pay-as-you-go rules to make health insurance more affordable and accessible, with particular attention to small businesses and health information technology.
- **[33] Sec. 508** — Expresses the House's view that Congress should restore the statutory pay-as-you-go rule in its original form from the Budget Enforcement Act of 1990 in order to reduce the deficit.
- **[34] Sec. 509** — Expresses Congress's view that the annual budget process should take into account the Financial Report of the United States Government, including data on net operating costs and long-term liabilities.
- **[35] Sec. 510** — Expresses the House's view that civilian federal employees should receive pay raises at the same time and in the same amount as members of the military.
- **[36] Sec. 511** — Expresses the House's view that all committees should identify wasteful and fraudulent spending in programs under their jurisdiction, and highlights existing budget provisions targeting improper payments, tax-gap enforcement, and annual program performance reviews.
- **[37] Sec. 512** — Expresses the House's view that additional legislation is needed to ensure states have resources to collect all owed child support and to pass 100 percent of those payments directly to families.
- **[38] Sec. 513** — Expresses the House's view that the federal government should pay the burial plot allowance for eligible spouses and children of veterans interred in state veterans cemeteries, consistent with pay-as-you-go rules.

### Reconciliation Instructions
- **[39] Sec. 601** — Instructs the House Committee on Education and Labor to report legislative changes that reduce the deficit by $75 million over fiscal years 2007 through 2012 and submit those recommendations by September 10, 2007.

## hres 8966 — 6 sections → 4 groups (parser: GpoText)

**Summary:** H. Res. 5 establishes the rules governing the 118th Congress by adopting prior rules with amendments. It introduces new fiscal guardrails, procedural orders, a select subcommittee to investigate COVID-19, and special floor procedures for specific energy legislation.

### Rules Adoption
- **[0] Fallback** — This is H. Res. 5, adopted by the House of Representatives on January 9, 2023, to establish the rules governing the 118th Congress.
- **[1] Sec. 1** — Adopts the rules from the 117th Congress as the starting rules for the 118th Congress, subject to amendments and additional orders specified in this resolution.

### Fiscal and Budget Rules
- **[2] Sec. 2** — Amends House standing rules to prohibit legislation that increases mandatory spending in specified windows, removes the automatic debt limit increase mechanism, and restricts amendments to appropriations bills that increase budget authority.
- **[3] Sec. 3** — Establishes several procedural orders for the 118th Congress, including reinstating the Holman Rule to allow spending cuts via appropriations amendments, voiding certain prior COVID-related House regulations, requiring sponsors to declare a single subject for each bill they introduce, restricting rules that waive germaneness points of order, and setting interim budget enforcement measures.

### COVID-19 Oversight
- **[4] Sec. 4** — Establishes a Select Subcommittee on the Coronavirus Pandemic under the Committee on Oversight and Accountability, defining its membership, investigative authority, and mandate to investigate pandemic origins, federal spending, vaccine policy, and related economic impacts with a final report due by January 2, 2025.

### Energy Legislation Procedures
- **[5] Sec. 5** — Sets specific floor procedures for considering H.R. 21 related to increasing oil and gas production alongside Strategic Petroleum Reserve drawdowns, and provides for expedited consideration of additional bills specified elsewhere in the section.

## sres 129397 — 2 sections → 1 groups (parser: GpoText)

**Summary:** This is a simple Senate organizational resolution from the 114th Congress establishing the default daily meeting time for the Senate. It sets noon (12:00 PM) as the standard time for the Senate to convene each day, unless the Senate votes to meet at a different time.

### Senate Meeting Time
- **[0] Fallback** — This is a Senate resolution introduced by Mr. McConnell on January 6, 2015, during the 114th Congress, establishing the daily meeting time of the Senate.
- **[1] Section** — This section sets the Senate's default daily meeting time to noon (12:00 PM) unless the Senate decides otherwise.

## sres 323852 — 21 sections → 3 groups (parser: GpoText)

**Summary:** This Senate resolution authorizes operating budgets for all major Senate committees for a two-year period running from March 1, 2025 through February 28, 2027. It sets individual spending caps for each committee covering staff, consultants, and other operational expenses across three sub-periods, while also establishing overall aggregate limits and a shared reserve fund. The resolution also specifies payment and voucher procedures governing how committee expenses are disbursed.

### Overall Spending Framework
- **[0] Fallback** — This resolution authorizes Senate committees to spend money for their operations across three time periods: March 1, 2025 through February 28, 2027.
- **[1] Sec. 1** — This section sets the total spending limits for all covered Senate committees combined across the three periods and establishes how committee expenses are to be paid and when vouchers are or are not required.

### Individual Committee Authorizations
- **[2] Sec. 2** — This section authorizes the Senate Agriculture, Nutrition, and Forestry Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $4.5 million, $7.7 million, and $3.2 million for each respective period.
- **[3] Sec. 3** — This section authorizes the Senate Armed Services Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $6.1 million, $10.4 million, and $4.4 million for each respective period.
- **[4] Sec. 4** — This section authorizes the Senate Banking, Housing, and Urban Affairs Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $5.1 million, $8.8 million, and $3.7 million for each respective period.
- **[5] Sec. 5** — This section authorizes the Senate Budget Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $4.6 million, $7.9 million, and $3.3 million for each respective period.
- **[6] Sec. 6** — This section authorizes the Senate Commerce, Science, and Transportation Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $6.3 million, $10.7 million, and $4.5 million for each respective period.
- **[7] Sec. 7** — This section authorizes the Senate Energy and Natural Resources Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $4.4 million, $7.5 million, and $3.1 million for each respective period.
- **[8] Sec. 8** — This section authorizes the Senate Environment and Public Works Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $4.1 million, $7.0 million, and $2.9 million for each respective period.
- **[9] Sec. 9** — This section authorizes the Senate Finance Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $7.6 million, $13.1 million, and $5.5 million for each respective period.
- **[10] Sec. 10** — This section authorizes the Senate Foreign Relations Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $6.1 million, $10.4 million, and $4.3 million for each respective period.
- **[11] Sec. 11** — This section authorizes the Senate Health, Education, Labor, and Pensions Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $7.8 million, $13.3 million, and $5.5 million for each respective period.
- **[12] Sec. 12** — This section authorizes the Senate Homeland Security and Governmental Affairs Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $8.4 million, $14.4 million, and $6.0 million for each respective period.
- **[13] Sec. 13** — This section authorizes the Senate Judiciary Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $9.1 million, $15.5 million, and $6.5 million for each respective period.
- **[14] Sec. 14** — This section authorizes the Senate Rules and Administration Committee to hire staff, spend funds, and use agency personnel, with spending capped at roughly $2.4 million, $4.0 million, and $1.6 million for each respective period.
- **[15] Sec. 15** — Authorizes the Senate Committee on Small Business and Entrepreneurship to spend up to approximately $9.5 million total (across three budget periods from March 2025 through February 2027) on staff, consultants, training, and other operational expenses.
- **[16] Sec. 16** — Authorizes the Senate Committee on Veterans' Affairs to spend up to approximately $9.2 million total (across three budget periods from March 2025 through February 2027) on staff, consultants, training, and other operational expenses.
- **[17] Sec. 17** — Authorizes the Senate Special Committee on Aging to spend up to approximately $7.1 million total (across three budget periods from March 2025 through February 2027) on staff, consultants, training, and other operational expenses.
- **[18] Sec. 18** — Authorizes the Senate Select Committee on Intelligence to spend up to approximately $18 million total (across three budget periods from March 2025 through February 2027) on staff, consultants, and other operational expenses.
- **[19] Sec. 19** — Authorizes the Senate Committee on Indian Affairs to spend up to approximately $6.4 million total (across three budget periods from March 2025 through February 2027) on staff, consultants, training, and other operational expenses.

### Shared Reserve Fund
- **[20] Sec. 20** — Establishes a special reserve fund—capped at roughly 7–8 percent of the Senate investigations account appropriations for each period—that any committee covered by this resolution may access on a needs basis with approval from the leadership of the Committee on Rules and Administration.

## hr 272091 — 1 sections → 1 groups (parser: Fallback)

**Summary:** This is a private immigration bill that grants permanent resident status to a specific individual, Jana Hlavaty. It retroactively deems her to have been lawfully admitted to the United States as a permanent resident as of August 26, 1969, under the Immigration and Nationality Act.

### Individual Immigration Relief
- **[0] Fallback** — This private law deems Jana Hlavaty to have been lawfully admitted to the United States as a permanent resident as of August 26, 1969, for purposes of the Immigration and Nationality Act.

## hr 154389 — 1 sections → 1 groups (parser: Fallback)

**Summary:** This bill establishes the 'Courthouse Affordability and Space Efficiency Act of 2025' (CASE Act), which appears to address courthouse space and cost management. The legislation is identified solely by its short title in the available sections.

### Bill Title
- **[0] Fallback** — Sets the short title of the legislation as the 'Courthouse Affordability and Space Efficiency Act of 2025' or the 'CASE Act'.

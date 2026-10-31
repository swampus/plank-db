// Optional test-only Playwright dependency, no frontend build toolchain.
// Set PLAYWRIGHT_MODULE / PLAYWRIGHT_BROWSER when using an existing installation.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.ORACLE_LAB_BASE||'http://localhost:8085';
const output=process.env.ORACLE_BROWSER_OUTPUT||'verification-results/audit-browser';
(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_BROWSER?{executablePath:process.env.PLAYWRIGHT_BROWSER}:{})});
 try{
  fs.mkdirSync(output,{recursive:true});
  const page=await browser.newPage({viewport:{width:1440,height:1200}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto(base+'/oracle-lab/');await page.waitForSelector('#grid .state');
  assert.equal(await page.locator('#grid .state').count(),16);
  assert.equal(await page.locator('#grid .valid').count(),1);
  async function run(){
   await page.getByRole('button',{name:'Run Aer simulation'}).click();
   await page.waitForFunction(()=>document.getElementById('status').textContent.startsWith('Aer simulation completed'),{},{timeout:45000});
   return JSON.parse(await page.locator('#raw').textContent());
  }
  let report=await run();assert.equal(report.top_measurement.value,9);assert(report.top_measurement.valid);assert(report.observed_valid_probability>.93);
  const reference=await (await page.request.get(base+'/oracle-lab/audit-reference.json')).json();
  const recorded=reference.measured_reference.find(row=>row.compiler==='optimized').stats;
  assert.equal(report.metrics.gate_counts.cx,recorded.full_transpiled_cx.median);
  assert.equal(await page.locator('#compiler-comparison tr').count(),9);
  assert((await page.locator('#compiler-comparison-note').textContent()).includes('recorded measurements'));
  await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.locator('#iterations').fill('6');report=await run();assert(report.observed_valid_probability<.12);
  assert.equal(await page.locator('#compiler-comparison tr').count(),0);
  await page.locator('#kind').selectOption('AND');await page.locator('#iterations').fill('');report=await run();assert.equal(report.analytical_solution_count,1);assert.equal(report.top_measurement.value,9);
  await page.locator('#kind').selectOption('OR');await page.locator('#iterations').fill('1');report=await run();assert.equal(report.theoretical_success_probability,null);assert.equal(await page.locator('#grid .valid').count(),7);
  await page.setViewportSize({width:390,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  // Slashless legacy demo URL must load its new comparison asset correctly too.
  await page.goto(base+'/oracle-lab');await run();assert.equal(await page.locator('#compiler-comparison tr').count(),9);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({status:'PASS',checks:['desktop live Aer','independent bit grid','recorded before/after metrics','no extrapolation to other settings','overshooting','AND auto','OR manual','mobile layout','slashless URL','no JavaScript errors']},null,2));
  console.log('PASS: live desktop/mobile Oracle Lab, measured comparison, overshooting, AND/OR, slashless URL, no JS errors');
 }finally{await browser.close()}
})().catch(error=>{console.error(error);process.exitCode=1});

async (page) => {
  const check = (condition, message) => { if (!condition) throw new Error(message); };
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => { window.alert = message => { (window.__alerts ||= []).push(message); }; });
  const dangerous = '<img src=x onerror="window.injected=true">';
  const citations = [{chunk:{docName:dangerous + '.md', headingPath:dangerous, content:'可核对原文'}, score:.8}];
  let uploads = 0, streamCalls = 0, plainCalls = 0;
  const json = (route, value, status=200) => route.fulfill({status, contentType:'application/json', body:JSON.stringify(value)});
  const frame = (event, value) => `event:${event}
data:${JSON.stringify(value)}

`;
  await page.route('**/api/**', async route => {
    const request = route.request(), path = '/api' + request.url().split('/api')[1].split('?')[0];
    if (path === '/api/kb/documents') {
      if (request.method() === 'POST') { uploads++; return json(route, {name:'uploaded.md', chunkCount:1}); }
      return json(route, [{id:1,name:dangerous+'.md',chunkCount:1}]);
    }
    if (request.method() === 'DELETE') return json(route,{error:'删除失败'},503);
    if (path === '/api/eval/run') return json(route,[{mode:'hybrid',total:38,hitAt5:1,hitAt1:.9}]);
    const question = request.postDataJSON().question;
    const answer = {question, answer:'第一行\n{JSON} [1]', citations, rejected:false};
    if (path === '/api/chat') { plainCalls++; return json(route, answer); }
    streamCalls++;
    if (question === 'fallback') return json(route, {error:'unsupported'},404);
    if (question === 'truncate') return route.fulfill({contentType:'text/event-stream',body:frame('delta',{text:'未完成'})});
    if (question === 'invalid') return route.fulfill({contentType:'text/event-stream',body:frame('delta',{text:'错误 [99]'})+frame('done',{answer:'本次回答的引用缺失或无效',citations:[],rejected:true})});
    return route.fulfill({contentType:'text/event-stream',body:
      frame('meta',{question}) + frame('delta',{text:'第一行\n'}) + frame('delta',{text:'{JSON} [1]'}) + frame('done',answer)});
  });
  await page.goto('http://127.0.0.1:18765');
  await page.waitForFunction(() => document.querySelector('#docList').textContent.includes('<img'));
  check(await page.locator('#docList img').count() === 0, 'file name HTML injection');
  async function ask(text) {
    await page.getByRole('textbox').fill(text);
    await page.getByRole('button',{name:'发送',exact:true}).click();
    await page.waitForFunction(() => !document.querySelector('#sendBtn').disabled);
    return page.locator('.msg.bot').last();
  }
  let bot = await ask('normal');
  check(await bot.locator('.body').textContent() === '第一行\n{JSON} [1]', 'multiline / JSON-like answer lost');
  check(await bot.locator('summary').count() === 1, 'citation missing');
  check(await bot.locator('img').count() === 0, 'citation HTML injection');
  await bot.locator('summary').click();
  check(await bot.locator('details').textContent().then(text => text.includes('可核对原文')), 'source excerpt missing');
  bot = await ask('fallback');
  check(await bot.locator('summary').count() === 1, 'JSON fallback citations missing');
  check(plainCalls === 1, 'unexpected fallback calls');
  bot = await ask('truncate');
  check((await bot.textContent()).includes('响应提前结束'), 'truncated stream accepted');
  check(plainCalls === 1, 'truncation caused duplicate generation');
  bot = await ask('invalid');
  check((await bot.locator('.body').textContent()).includes('引用缺失或无效'), 'final validation did not replace draft');
  check(await bot.locator('summary').count() === 0, 'rejected answer retained citations');
  await page.locator('input[type=file]').setInputFiles(['kb/java-collections.md', 'kb/redis-cache-lock.md']);
  await page.getByRole('button',{name:'入库',exact:true}).click();
  await page.waitForFunction(() => !document.querySelector('#uploadBtn').disabled);
  check(uploads === 2, 'multiple files were not all uploaded');
  await page.getByRole('button',{name:'删除',exact:true}).click();
  await page.waitForFunction(() => !document.querySelector('#docList button').disabled);
  check(await page.locator('#docList li').count() === 1, 'failed deletion removed row');
  await page.getByRole('button',{name:'跑检索评测（Hit@5）',exact:true}).click();
  await page.waitForFunction(() => document.querySelector('#evalTitle').textContent.includes('38'));
  check((await page.locator('#evalResult').textContent()).includes('100.0%'), 'evaluation failed');
  await page.setViewportSize({width:390,height:844});
  check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile horizontal overflow');
  check(await page.evaluate(() => !window.injected), 'HTML executed');
  check(errors.length === 0, errors.join('\n'));
  await page.screenshot({path:'output/playwright/browser-smoke.png',fullPage:true});
  const result = {passed:true,streamCalls,plainCalls,uploads,checks:['send','multiline SSE','JSON fallback','truncated stream','citation validation','HTML escaping','multiple upload','delete failure','evaluation','mobile layout']};
  await page.evaluate(result => { window.__smokeResult = result; }, result);
  return result;
}

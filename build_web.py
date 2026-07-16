#!/usr/bin/env python3
"""앱 화면(index.html)을 그대로 가져와 브라우저용 단일 파일로 뽑는다.
   앱을 고치면 이걸 다시 돌리면 된다 — 두 벌로 관리하지 않는다."""
import re, sys, pathlib

SRC = pathlib.Path(__file__).parent / 'app/src/main/assets/index.html'
OUT = pathlib.Path(__file__).parent / 'docs/index.html'

s = SRC.read_text(encoding='utf-8')

# 1) 폰트: 앱에선 assets 에 심어 뒀지만 웹에선 경로가 없다 → CDN 으로
font_block = re.search(r'  /\* 숫자가 이 앱의 내용이다.*?\n  \}\n', s, re.S)
assert font_block, '폰트 블록을 못 찾음'
s = s.replace(font_block.group(0), '')
s = s.replace('<style>',
  '''<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Archivo:wght@500;700&display=swap" rel="stylesheet">
<style>''', 1)

# 2) 제목
s = s.replace('<title>복습 도장판</title>', '<title>복습 도장판 — 웹 미리보기</title>')

# 3) 브라우저에서는 알림·위젯이 없다는 걸 설정에서 분명히 말한다
s = s.replace("""  function renderNotifyHelp(){
    var box=$('notifyHelp');
    if(!N){ box.innerHTML=''; return; }""",
"""  function renderNotifyHelp(){
    var box=$('notifyHelp');
    if(!N){
      box.innerHTML='<div class="warn" style="background:#F1F4F9;border-color:var(--line)">'+
        '<p style="color:var(--ink-2);margin:0">알림과 홈화면 위젯은 <b>설치형 앱에서만</b> 동작합니다. '+
        '여기서는 시각만 저장되고 울리지는 않습니다.</p></div>';
      return;
    }""")

# 4) 웹이라는 걸 머리에 한 줄
s = s.replace('<div class="brand">복습 도장판<em id="brandSub">1 · 7 · 30</em></div>',
              '<div class="brand">복습 도장판 <span id="webTag">웹</span><em id="brandSub">1 · 7 · 30</em></div>')
s = s.replace('  .brand em{', '''  #webTag{font-size:9px;font-weight:800;letter-spacing:.06em;color:var(--ink-3);
    border:1px solid var(--line);border-radius:5px;padding:2px 5px;vertical-align:2px;margin-left:2px}
  .brand em{''')

# 5) 저장소 키를 앱과 겹치지 않게 (같은 기기에서 둘 다 써도 안 섞이도록)
s = s.replace("var KEY = 'bokseup-dojangpan-v1';", "var KEY = 'bokseup-dojangpan-web-v1';")

OUT.write_text(s, encoding='utf-8')
print(f'뽑음: {OUT}  ({OUT.stat().st_size:,} bytes)')

# 점검
assert 'archivo-500.woff2' not in s, '폰트 경로가 남아 있음'
assert 'fonts.googleapis.com' in s, 'CDN 링크가 없음'
assert "window.Android" in s, '브릿지 분기가 사라짐'
print('점검 통과: 폰트 CDN 전환 · 브릿지 분기 유지')

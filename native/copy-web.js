// 저장소 맨 위의 웹 파일을 native/www 로 복사 (앱에 들어갈 화면)
const fs=require('fs'),path=require('path');
const root=path.join(__dirname,'..'),out=path.join(__dirname,'www');
const files=['index.html','sw.js','manifest.json','icon.svg','icon-192.png','icon-512.png','icon-maskable-512.png','pretendard-sub.woff2'];
fs.rmSync(out,{recursive:true,force:true});fs.mkdirSync(out,{recursive:true});
for(const f of files){const src=path.join(root,f);if(!fs.existsSync(src))throw new Error('없는 파일: '+f);fs.copyFileSync(src,path.join(out,f));}
console.log('www 준비 완료:',files.length,'개 파일');

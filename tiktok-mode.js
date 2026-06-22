// ==UserScript==
// @name         视频网站抖音模式 (TikTok Mode)
// @namespace    http://tampermonkey.net/
// @version      3.0
// @description  将网页首页或具体电视剧页面转为抖音上下滑动模式，支持白小唐等Maccms影视站，以及MissAV等网站。
// @author       You
// @match        *://*.baixiaotangtop.com/*
// @match        *://missav.com/*
// @match        *://*.missav.com/*
// @match        *://missav.ws/*
// @match        *://*.missav.ws/*
// @match        *://missav.ai/*
// @match        *://*.missav.ai/*
// @grant        GM_addStyle
// @run-at       document-end
// ==/UserScript==

(function() {
    'use strict';

    // 【核心破局点】如果是被我们作为 iframe 嵌入的页面，直接全屏显示视频并接管控制
    if (window.top !== window.self && location.search.includes('tiktok=1')) {
        GM_addStyle(`
            body, html { background: #000 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; width: 100vw !important; height: 100vh !important; }
            body > * { display: none !important; opacity: 0 !important; }
            .plyr, video, .max-w-7xl, .player-container, .MacPlayer { display: block !important; opacity: 1 !important; visibility: visible !important; }
            .plyr__controls, .dplayer-controls, .MacPlayer span { display: none !important; }
            video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; object-fit: contain !important; z-index: 99999999 !important; background: #000 !important; }
        `);

        window.addEventListener('message', (e) => {
            const v = document.querySelector('video');
            if (!v) return;
            if (e.data === 'play') { 
                v.muted = false; 
                v.play().catch(()=>{}); 
            }
            if (e.data === 'pause') { 
                v.pause(); 
            }
            if (e.data === 'mute_play') {
                v.muted = true;
                v.play().catch(()=>{});
            }
        });

        // 自动静音播放
        const autoPlayInterval = setInterval(() => {
            const v = document.querySelector('video');
            if (v) {
                v.muted = true;
                v.play().catch(()=>{});
                clearInterval(autoPlayInterval);
            }
        }, 500);
        return; // 终止后续的 UI 生成
    }

    function GM_addStyle(css) {
        const style = document.createElement('style');
        style.textContent = css;
        document.head.appendChild(style);
    }

    GM_addStyle(`
        #tiktok-mode-btn { position: fixed; bottom: 50px; right: 20px; background: #ff0050; color: white; border: none; border-radius: 50px; padding: 12px 20px; font-size: 16px; font-weight: bold; cursor: pointer; z-index: 999999; box-shadow: 0 4px 10px rgba(0,0,0,0.3); }
        #tiktok-container { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: #000; z-index: 9999999; display: none; overflow-y: scroll; scroll-snap-type: y mandatory; -webkit-overflow-scrolling: touch; scrollbar-width: none; }
        #tiktok-container::-webkit-scrollbar { display: none; }
        .tiktok-item { width: 100vw; height: 100vh; scroll-snap-align: start; scroll-snap-stop: always; position: relative; display: flex; justify-content: center; align-items: center; flex-shrink: 0; background: #111; }
        .tiktok-video { width: 100%; height: 100%; object-fit: contain; }
        .tiktok-close { position: fixed; top: 20px; left: 20px; color: rgba(255, 255, 255, 0.8); font-size: 30px; cursor: pointer; z-index: 99999999; background: rgba(0,0,0,0.5); width: 40px; height: 40px; border-radius: 50%; display: flex; justify-content: center; align-items: center; line-height: 1; }
        .tiktok-title { position: absolute; bottom: 40px; left: 20px; color: white; font-size: 16px; font-weight: bold; text-shadow: 1px 1px 3px rgba(0,0,0,0.8); z-index: 10; pointer-events: none; }
        .tiktok-loading { position: absolute; color: white; font-size: 16px; z-index: 5; display: none; }
        .tiktok-play-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: none; justify-content: center; align-items: center; background: rgba(0,0,0,0.3); z-index: 20; cursor: pointer; }
        .tiktok-play-overlay::after { content: "▶"; font-size: 80px; color: rgba(255,255,255,0.8); text-shadow: 0 4px 10px rgba(0,0,0,0.5); }
    `);

    let videoItems = [];
    let observer;

    const btn = document.createElement('button');
    btn.id = 'tiktok-mode-btn';
    btn.innerText = '🎵 抖音模式';
    document.body.appendChild(btn);

    function collectVideoLinks() {
        let items = [];
        const isMissAV = location.hostname.includes('missav');

        if (isMissAV) {
            // 如果是详情页，先添加当前视频
            if (location.pathname.length > 3) {
                items.push({
                    title: document.title.replace(' - MissAV.ai', '') || '当前视频',
                    detailUrl: location.href
                });
            }

            const links = Array.from(document.querySelectorAll('a')).filter(a => {
                return /^https?:\/\/[^\/]+\/([a-z]{2}\/)?[a-zA-Z0-9]+[-_][a-zA-Z0-9-_]+(\?.*)?$/i.test(a.href);
            });
            const uniqueLinks = [...new Map(links.map(a => [a.href.split('?')[0], a])).values()];
            
            uniqueLinks.forEach(a => {
                if (a.href.split('?')[0] === location.href.split('?')[0]) return; 
                let title = a.innerText.trim();
                if (!title) {
                    const img = a.querySelector('img');
                    if (img) title = img.alt || title;
                }
                if (!title) {
                    const parts = a.href.split('?')[0].split('/');
                    title = parts[parts.length - 1];
                }
                items.push({
                    title: title || '推荐视频',
                    detailUrl: a.href
                });
            });
        } else {
            // 白小唐逻辑
            if (location.pathname.includes('/voddetail/') || location.pathname.includes('/vodplay/')) {
                const links = Array.from(document.querySelectorAll('a[href*="/vodplay/"]'));
                const uniqueLinks = [...new Map(links.map(a => [a.href, a])).values()];
                items = uniqueLinks.map(a => ({ title: a.innerText.trim(), detailUrl: a.href }));
            } else {
                const links = Array.from(document.querySelectorAll('a[href*="/voddetail/"]'));
                const uniqueLinks = [...new Map(links.map(a => [a.href, a])).values()];
                items = uniqueLinks.map(a => {
                    let title = a.title || a.innerText.trim();
                    if (!title) { const img = a.querySelector('img'); if (img) title = img.alt || title; }
                    return { title: title || '未知视频', detailUrl: a.href };
                }).filter(item => item.title);
            }
        }
        return items;
    }

    btn.addEventListener('click', () => {
        videoItems = collectVideoLinks();
        if (videoItems.length === 0) {
            alert('当前页面没有找到可用的视频链接！');
            return;
        }
        initTikTokUI();
    });

    function initTikTokUI() {
        let container = document.getElementById('tiktok-container');
        if (container) {
            container.style.display = 'block';
            document.body.style.overflow = 'hidden';
            return;
        }

        container = document.createElement('div');
        container.id = 'tiktok-container';
        
        const closeBtn = document.createElement('div');
        closeBtn.className = 'tiktok-close';
        closeBtn.innerHTML = '×';
        closeBtn.onclick = () => {
            container.style.display = 'none';
            document.body.style.overflow = '';
            document.querySelectorAll('.tiktok-iframe').forEach(v => {
                v.contentWindow.postMessage('pause', '*');
            });
        };
        container.appendChild(closeBtn);

        videoItems.forEach((item, index) => {
            const itemDiv = document.createElement('div');
            itemDiv.className = 'tiktok-item';
            itemDiv.dataset.index = index;

            const loading = document.createElement('div');
            loading.className = 'tiktok-loading';
            loading.innerText = '正在加载播放器...';
            loading.style.display = 'block';

            // 真正的视频播放 iframe
            const iframe = document.createElement('iframe');
            iframe.className = 'tiktok-iframe';
            // 添加 tiktok=1 参数以便在 iframe 中被脚本识别
            iframe.src = item.detailUrl + (item.detailUrl.includes('?') ? '&' : '?') + 'tiktok=1';
            iframe.style.cssText = 'width:100vw; height:100vh; border:none; position:absolute; top:0; left:0; pointer-events:none; z-index: 1;';
            
            iframe.onload = () => {
                loading.style.display = 'none';
            };
            
            const title = document.createElement('div');
            title.className = 'tiktok-title';
            title.innerText = (item.title || `第 ${index + 1} 个视频`) + "\n(轻触屏幕开启声音/暂停)";

            const playOverlay = document.createElement('div');
            playOverlay.className = 'tiktok-play-overlay';

            // 用于捕获点击的透明遮罩，并向 iframe 发送指令
            const clickLayer = document.createElement('div');
            clickLayer.style.cssText = 'position:absolute; top:0; left:0; width:100%; height:100%; z-index: 20; cursor:pointer;';
            let isMuted = true;
            let isPaused = false;
            
            clickLayer.onclick = () => {
                if (isMuted) {
                    isMuted = false;
                    isPaused = false;
                    iframe.contentWindow.postMessage('play', '*');
                    playOverlay.style.display = 'none';
                } else {
                    isPaused = !isPaused;
                    if (isPaused) {
                        iframe.contentWindow.postMessage('pause', '*');
                        playOverlay.style.display = 'flex';
                    } else {
                        iframe.contentWindow.postMessage('play', '*');
                        playOverlay.style.display = 'none';
                    }
                }
            };

            const debugText = document.createElement('div');
            debugText.className = 'tiktok-debug';
            debugText.style.cssText = 'position:absolute; top:80px; left:20px; color:lime; font-size:12px; z-index:100; text-shadow: 1px 1px 2px black; word-break: break-all; pointer-events:none;';
            debugText.innerText = "【模式】原生 iframe 完美嵌入播放";

            itemDiv.appendChild(iframe);
            itemDiv.appendChild(loading);
            itemDiv.appendChild(playOverlay);
            itemDiv.appendChild(clickLayer);
            itemDiv.appendChild(title);
            itemDiv.appendChild(debugText);
            container.appendChild(itemDiv);
        });

        document.body.appendChild(container);
        container.style.display = 'block';
        document.body.style.overflow = 'hidden';

        observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                const iframe = entry.target.querySelector('iframe');
                if (entry.isIntersecting) {
                    if (iframe && iframe.contentWindow) {
                        iframe.contentWindow.postMessage('mute_play', '*');
                    }
                } else {
                    if (iframe && iframe.contentWindow) {
                        iframe.contentWindow.postMessage('pause', '*');
                    }
                }
            });
        }, { threshold: 0.5 });

        document.querySelectorAll('.tiktok-item').forEach(el => observer.observe(el));
    }
})();
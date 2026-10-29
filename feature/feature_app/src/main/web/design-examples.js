(() => {
  Object.assign(messages, {
    fillDesignExample: ['填入示例内容', 'Fill example content']
  });
  const section = document.querySelector('#designEditor .dialog-section');
  if (!section) return;
  const button = document.createElement('button');
  button.type = 'button';
  button.dataset.i18n = 'fillDesignExample';
  button.textContent = tr('fillDesignExample');
  section.querySelector('h3').appendChild(button);
  const presets = {
    MENU: { title: '晨光咖啡', subtitle: '新鲜现制 · 每日供应', accent: '#d7aa55', background: '#10241d', items: '招牌拿铁 | ¥28\n澳白 | ¥26\n冷萃 | ¥24\n巴斯克芝士 | ¥25' },
    WELCOME: { title: '诚挚欢迎\n贵宾莅临', subtitle: '今日会议 · 14:00 · 云庭厅', accent: '#b99a61', background: '#f3f5f4', items: '主会场 | 云庭厅 A\n签到处 | 大堂服务台' },
    PROMOTION: { title: '限时大促\n全场低至 5 折', subtitle: '到店即享 · 数量有限', accent: '#ffe36b', background: '#e5362c', items: '活动日期 | 9 月 20 日 - 9 月 30 日\n会员福利 | 额外 95 折' },
    SCHEDULE: { title: '今日会议安排', subtitle: '2026 年 9 月 20 日', accent: '#167a59', background: '#eef2f0', items: '09:00 - 10:30 季度经营复盘 | 云庭厅 A\n11:00 - 12:00 产品发布准备会 | 云庭厅 B\n14:00 - 16:00 合作伙伴交流会 | 宴会厅\n16:30 - 17:30 项目周会 | 会议室 3' }
  };
  button.addEventListener('click', () => {
    const value = presets[document.getElementById('designType').value] || presets.MENU;
    document.getElementById('designTitle').value = value.title;
    document.getElementById('designSubtitle').value = value.subtitle;
    document.getElementById('designAccent').value = value.accent;
    document.getElementById('designBackground').value = value.background;
    document.getElementById('designItems').value = value.items;
    updatePreview();
  });
  localize();
})();

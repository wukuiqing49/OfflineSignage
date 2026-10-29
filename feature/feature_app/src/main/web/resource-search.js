(() => {
  Object.assign(messages, {
    searchResources: ['搜索资源', 'Search resources'],
    searchResourcesPlaceholder: ['按名称、类型或来源筛选', 'Filter by name, type, or source'],
    noMatchingResources: ['没有匹配的资源。', 'No matching resources.']
  });
  state.resourceSearchQuery = '';
  const libraryHead = document.querySelector('#resourcesPanel .surface-head');
  if (!libraryHead) return;
  const search = document.createElement('input');
  search.type = 'search'; search.id = 'resourceSearch'; search.autocomplete = 'off';
  search.setAttribute('aria-label', tr('searchResources'));
  search.placeholder = tr('searchResourcesPlaceholder');
  search.style.cssText = 'flex:1 1 160px;max-width:220px;min-width:0';
  const baseRenderResources = renderResources;
  renderResources = function () {
    baseRenderResources();
    const query = state.resourceSearchQuery.trim().toLocaleLowerCase();
    if (!query) return;
    const rows = [...document.querySelectorAll('#resourceRows .content-row')];
    let shown = 0;
    rows.forEach(row => {
      const match = row.textContent.toLocaleLowerCase().includes(query);
      row.hidden = !match;
      if (match) shown += 1;
    });
    if (!shown && rows.length) {
      const empty = document.createElement('div'); empty.className = 'empty'; empty.textContent = tr('noMatchingResources');
      document.getElementById('resourceRows').appendChild(empty);
    }
  };
  search.addEventListener('input', () => { state.resourceSearchQuery = search.value; renderResources(); });
  libraryHead.appendChild(search);
  localize();
})();

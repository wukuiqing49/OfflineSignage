(() => {
  const libraryHead = document.querySelector('#resourcesPanel .surface-head');
  if (!libraryHead) return;
  libraryHead.style.flexWrap = 'wrap';
  const filterButton = document.createElement('button');
  filterButton.id = 'toggleUnscheduledResources';
  filterButton.type = 'button';
  const updateLabel = () => { filterButton.textContent = tr(state.resourceFilterUnscheduled ? 'showAllResources' : 'showUnscheduled'); };
  updateLabel();
  filterButton.addEventListener('click', () => {
    state.resourceFilterUnscheduled = !state.resourceFilterUnscheduled;
    updateLabel();
    renderResources();
  });
  libraryHead.appendChild(filterButton);
  localize();
})();

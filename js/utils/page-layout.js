import PageSidebar from "../../components/layout/page-sidebar.js?v=streaming-20260709a";

export function installPageSidebar(active) {
    const sidebar = document.querySelector(".page-sidebar");

    if (!sidebar) return;

    sidebar.innerHTML = PageSidebar(active);
}


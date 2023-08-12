
const scrollSpeed = 20;
const numPages = 3;

let currentPage = 1;
let scrollToPosition = null;

function main() {
    // document.getElementById("prev-page-button").addEventListener("click", prevPage);
    // document.getElementById("next-page-button").addEventListener("click", nextPage);
    updateScroll();
}

function nextPage() {
    if (currentPage >= numPages) return;
    if (scrollToPosition != null) return;
    currentPage++;
    updateScroll();
}

function prevPage() {
    if (currentPage <= 1) return;
    if (scrollToPosition != null) return;
    currentPage--;
    updateScroll();
}

function updateScroll() {
    const pageWidth = document.documentElement.scrollWidth / 3;
    window.scroll({
        top: 0,
        left: pageWidth * (currentPage - 1),
        behavior: "smooth"
    });
}

window.onload = main;

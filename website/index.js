
const scrollSpeed = 20;
const numPages = 3;

let currentPage = 1;
let scrollToPosition = null;

function main() {
    bindEventListeners(document.getElementsByClassName("to-main-page"), 1);
    bindEventListeners(document.getElementsByClassName("to-about-page"), 2);
    bindEventListeners(document.getElementsByClassName("to-imprint-page"), 3);

    updateScroll();
}

function bindEventListeners(elements, pageNumber) {
    Array.from(elements).forEach(element => {
        element.addEventListener("click", () => {
            currentPage = pageNumber;
            updateScroll();
        });
    });
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

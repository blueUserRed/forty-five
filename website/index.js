
const scrollSpeed = 20;
const numPages = 3;

let currentPage = 1;
let scrollToPosition = null;

let lastYScrollPosition = 0;

function main() {
    bindEventListeners(document.getElementsByClassName("to-main-page"), 1);
    bindEventListeners(document.getElementsByClassName("to-about-page"), 2);
    bindEventListeners(document.getElementsByClassName("to-imprint-page"), 3);

    addEventListener("scroll", () => lastYScrollPosition = window.scrollY);
    addEventListener("resize", () => fixScroll());
    // updateScroll();
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

function fixScroll() {
    const pageWidth = document.documentElement.scrollWidth / 3;
    window.scroll({
        top: lastYScrollPosition,
        left: pageWidth * (currentPage - 1),
        behavior: "instant"
    });
}

window.onload = main;

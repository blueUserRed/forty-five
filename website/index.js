
const scrollSpeed = 20;
const numPages = 3;

let currentPage = 1;
let scrollToPosition = null;

let lastYScrollPosition = 0;

let navbarDependents;
let navbarOpen = false;

let curPageDependents;

function main() {
    doPageChangeListeners();
    doNavbarListeners();
    updateCurPageClasses();

    // fix scroll offset when resizing
    addEventListener("scroll", () => lastYScrollPosition = window.scrollY);
    addEventListener("resize", () => fixScroll());

    updateScroll();
}

function doNavbarListeners() {
    navbarDependents = Array.from(document.getElementsByClassName("navbar-dependent"));
    addEventListener("click", () => {
        closeNavbar();
    });
    Array.from(document.getElementsByClassName("opens-navbar")).forEach(element => {
        element.addEventListener("click", e => {
            if (navbarOpen) return;
            e.stopPropagation();
            openNavbar();
        });
    });
}

function openNavbar() {
    if (navbarOpen) return;
    navbarOpen = true;
    navbarDependents.forEach(element => element.classList.add("navbar-open"));
}

function closeNavbar() {
    if (!navbarOpen) return;
    navbarOpen = false;
    navbarDependents.forEach(element => element.classList.remove("navbar-open"));
}


function doPageChangeListeners() {
    bindScrollEventListeners(document.getElementsByClassName("to-main-page"), 1);
    bindScrollEventListeners(document.getElementsByClassName("to-about-page"), 2);
    bindScrollEventListeners(document.getElementsByClassName("to-imprint-page"), 3);

    curPageDependents = Array.from(document.getElementsByClassName("cur-page-dependent"));
}

function bindScrollEventListeners(elements, pageNumber) {
    Array.from(elements).forEach(element => {
        element.addEventListener("click", () => {
            currentPage = pageNumber;
            updateCurPageClasses();
            updateScroll();
        });
    });
}

function updateCurPageClasses() {
    curPageDependents.forEach(element => {
        for (let i = 1; i <= numPages; i++) {
            element.classList.remove(`cur-page-${i}`)
        }
        element.classList.add(`cur-page-${currentPage}`)
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

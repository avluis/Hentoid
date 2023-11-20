document.addEventListener("DOMContentLoaded", (event) => {
    aaa();
});

function eval() {
  return new Promise((resolve) => {
    setTimeout(() => {
        if (document.querySelector("#previews button") != null) {
            document.querySelector("#previews button").click();
            resolve(true);
        } else {
            resolve(false);
        }
    }, 1500);
  });
}

async function aaa() {
    console.info("ready");
    var result = await eval();
    if (!result) result = await eval();
    if (!result) result = await eval();
}
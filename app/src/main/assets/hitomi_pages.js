$galleryInfo;
var pages = new Array(galleryinfo['files'].length);
for (var i = 0; i < galleryinfo['files'].length; i++) {
    if ($download_avif && 1 == galleryinfo['files'][i]['hasavif']) {
        pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i], 'avif');
    } else {
        pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i], 'webp');
    }
    //if (galleryinfo['files'][i]['haswebp']) {
        //pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i], 'webp');
    //} else {
//        pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i]);
//    }
    // url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i], 'avif', undefined, 'a');
}
JSON.stringify(pages);
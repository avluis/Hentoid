$galleryInfo;
var pages = new Array(galleryinfo['files'].length);
for (var i = 0; i < galleryinfo['files'].length; i++) {
    if ($webp && galleryinfo['files'][i]['haswebp']) {
        pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i], 'webp', undefined, 'a');
    } else {
        pages[i] = url_from_url_from_hash(galleryinfo['id'], galleryinfo['files'][i]);
    }
}
JSON.stringify(pages);
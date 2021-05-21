if (typeof origFetch === 'undefined') {
    const origFetch = fetch; /* window.fetch*/
    fetch = async (...args) => {
      /* console.log("fetch called with args:", args);*/

      /* Send the calling args to the webview */
      fetchHandler.onFetchCall(args[0], args[1].body);
      const response = await origFetch(...args);

      /* we don't need the response data for Hentoid... yet
      response
        .clone()
        .json()
        .then(body => ajaxHandler.ajaxBegin(args[0], args[1].body))
        .catch(err => console.error(err))
      ;
      */

      /* the original response is resolved unmodified */
      return response;
    };
}
if (typeof origFetch === 'undefined') {
    const origFetch = fetch; /* window.fetch*/
    fetch = async (...args) => {
      /* console.log("fetch called with args:", args);*/

      /* Send the calling args to the app */
      if (typeof (fetchHandler) != 'undefined') {
        var body = ""
        if (args.length > 1) body = args[1].body
        fetchHandler.onFetchCall(args[0], body);
      }

      const response = await origFetch(...args);

      /* Send the response to the app if handler is set */
      if (typeof (fetchResponseHandler) != 'undefined') {
         var body = ""
         if (args.length > 1) body = args[1].body
         response
            .clone()
            .then(r => fetchResponseHandler.onCall(args[0], body, r.text()))
            .catch(err => console.error(err));
      }

      /* the original response is resolved unmodified */
      return response;
    };
}
**SimpleDashCam**

This app is a simple video recorder that records a video, splits it in chunks and uploads them to Flickr. It is written entirely in Java and uses various Android components and 3rd party libraries.

This app uses these 3rd party libraries:
- https://github.com/WritingMinds/ffmpeg-android For splitting videos into smaller chunks
- https://developers.google.com/api-client-library/java/google-oauth-java-client/oauth1 For making OAuth1 calls to Flickr.

Various API endpoints used to communicate with Flickr.
- https://www.flickr.com/services/oauth/request_token
- https://www.flickr.com/services/oauth/authorize
- https://www.flickr.com/services/oauth/access_token
- https://up.flickr.com/services/upload/
- https://www.flickr.com/services/rest

Read more about Flickr API at: https://www.flickr.com/services/api/

If you want to compile and run it yourself, get a pair of API key and secret from Flickr and put it in **StartPage.java** as **FLICKR_API_KEY** and **FLICKR_API_SECRET** constants.
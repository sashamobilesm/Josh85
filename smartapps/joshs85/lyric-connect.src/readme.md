# Installation Instructions
1. log into https://graph.api.smartthings.com/
2. Click My Smart Apps
3. Click Settings
4. Click add new repository
5. Enter joshs85 for Owner
6. Enter SmartThingsPublic for the Name
7. Enter master for the branch.
8. Click save when completed
9. Do the same on the my device handlers page.
10. On the My Device handlers pages, click Update from repo and check the check box next to smartapps/joshs85/lyric-connect.src/lyric-connect.groovy
11. Check the publish box at the bottom of the screen and then click Execute Update
12. On the My Device handlers pages, click Update from repo and check the check box next to devicetypes/joshs85/lyric-leak-sensor.src/lyric-leak-sensor.groovy
13. Check the publish box at the bottom of the screen and then click Execute Update

### Create A HoneyWell Developer account
1. go to https://developer.honeywell.com
2. Create an account and login.
3. Click My Apps > Create New App
4. Enter any name you like
5. Enter this for the callback URL: https://graph.api.smartthings.com/oauth/callback
6. Click Save Changes

### Configure the app settings and enable oAuth.
1. In the SmartThings API, Click on Device Handlers
2. Click the new Lyric (Connect) app
3. Click App Settings
4. Click Settings and enter the API Key and Secret from the honeywell app you created above.
5. Click oAuth > Enable oAuth
6. When done, press Update.

Now you can install the app in smart things by going to Automation > Add a Smart App > My Apps > Lyric (Connect)

BottomBarDrawer
===============

 BottomBarDrawerLayout acts as a top-level container for window content that allows for interactive "drawer" view to be pulled out from the bottom edge of the window. This version of drawer is working with always visible part of the drawer that serves as bar that when dragged or touched performs open/close operation.
 
 The code is mostly based on the [DrawerLayout][1] class from the Android support library v4 API. 
 
 DrawerLayout was rewritten to support vertical scrolling of drawer view, specifically to allow users to drag views from bottom to top. The idea is based on the [Google play music Android application][2] that allows opening the mostly hidden drawer view from the bottom by dragging its visible content to the top. In the music app it is used to display current play list and to manage music controller.
 
 As in Google play music this layout support these features:
 - setting a visible content that is draggable by user from bottom to top
 - fading the main content when drawer view is being opened
 - clearing out the drawer view content when drawer is being opened
 - allowing to open and close drawer view by either tapping on the visible content or by dragging the view



Developed By
============

* Martin Rajniak - <rajniak.m@gmail.com>



License
=======

    Copyright 2013 Martin Rajniak

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
    
    


[1]: http://developer.android.com/reference/android/support/v4/widget/DrawerLayout.html
[2]: https://play.google.com/store/apps/details?id=com.google.android.music

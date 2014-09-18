BlueChat
=========

Bluetooth instant messaging app for Android

Screenshots here (more to be posted soon): http://imgur.com/Vy45zJ7.jpg

How it works:
  - To send messages and images, we transmit them in formatted byte arrays in the following format:
      <blockquote>
      [ Message Type, Sender Name Length, ...Body Length**..., Sender ID, Sender Name, Body ]
      </blockquote>
      
    ** The Body Length is separated into tens digits (from least to most significant digit) just in case the body length exceeds the size of a byte.

Notes:
  - If having connection problems, make sure the host creates a room before a client tries to join
  
Current Bugs:
  - Sometimes quitting and rejoining a chatroom is a bit buggy

Download Current Version: https://www.mediafire.com/?sy1y7448i6adfts

var socket;
var input = document.getElementById("input");


(function() {
  'use strict';

  var left = document.getElementById("left");
  var right = document.getElementById("right");

  function append(elem, text) {
    elem.innerHTML += "<br/>" +  text; // TODO
  }
  
  //TODO support wss:// somehow
  var ws = new WebSocket('ws://' + window.location.host + '/join');
  socket = ws;
  
  ws.onmessage = function(event) {
    var message = event.data;
    console.log('Received:', event);
    append(left, message);
  };
  
  
}());

function send() {
  var value = input.value;

  if(value) {
    socket.send(value);
  }
}

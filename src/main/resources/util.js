  Array.prototype.remove = function(matcher) {
      for(var i = this.length - 1; i >= 0; i--) {
            if(matcher(this[i], i)) {
               this.splice(i, 1);
            }
       }
  };

  Array.prototype.modify = function(matcher, modifier) {
      for(var i = this.length - 1; i >= 0; i--) {
            if(matcher(this[i], i)) {
               this[i] = modifier(this[i]);
            }
       }
  };

  Array.prototype.findFirst = function (predicate, thisValue) {
      var arr = Object(this);
      if (typeof predicate !== 'function') {
          throw new TypeError();
      }
      for(var i=0; i < arr.length; i++) {
          if (i in arr) {  // skip holes
              var elem = arr[i];
              if (predicate.call(thisValue, elem, i, arr)) {
                  return elem;  // (1)
              }
          }
      }
      return undefined;  // (2)
  }
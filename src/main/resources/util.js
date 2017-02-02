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
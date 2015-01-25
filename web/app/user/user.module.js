'use strict';

/** Everything user-related. */
angular.module('stork.user', [
  'ngCookies', 'stork'
])

.service('user', function (stork, $location, $rootScope, $cookies) {
  this.user = function () {
    return $rootScope.$user;
  };
  this.login = function (info) {
    if (info) {
      var f = stork.login(info);
      f.then(this.saveLogin, this.forgetLogin);
      return f;
    }
  };
  this.saveLogin = function (u) {
    $rootScope.$user = u;
    $cookies.email = u.email;
    $cookies.hash = u.hash;
  };
  this.forgetLogin = function () {
    delete $rootScope.$user;
    delete $cookies.email;
  };
  this.checkAccess = function (redirectTo) {
    if (!$rootScope.$user)
      $location.path(redirectTo||'/');
  };

  // If there's a cookie, attempt to log in.
  var u = {
    email: $cookies.email,
    hash:  $cookies.hash
  };

  if (u.email && u.hash)
    this.login(u);
  else
    this.forgetLogin();
})

.controller('User', function ($scope, $modal, $location, user) {
  /* If info is given, log the user in. Otherwise show modal. */
  $scope.login = function (info, then) {
    if (!info)
      return $modal({
        title: 'Log in',
        container: 'body',
        contentTemplate: '/app/user/login.html'
      });
    return user.login(info).then(function (v) {
      if (then)
        then(v);
      $modal({
        title: "Welcome!",
        content: "You have successfully logged in.",
        show: true
      });
    }, function (error) {
      $scope.error = error;
    });
  };

  /* Log the user out. */
  $scope.logout = function () {
    user.forgetLogin();
    $location.path('/');
    $modal({
      content: "You have successfully logged out.",
      show: true
    });
  };
})

.controller('Register', function ($scope, stork, $location, user, $modal) {
  $scope.register = function (u) {
    return stork.register(u).then(function (d) {
      user.saveLogin(d);
      $modal({
        title: "Welcome!",
        content: "Thank for you registering with StorkCloud! "+
                 "You have been logged in automatically.",
        show: true
      });
      $location.path('/');
      delete $scope.user;
    }, function (e) {
      $modal({
        title: "There was a problem registering",
        content: e.error,
        show: true
      });
    })
  }
});

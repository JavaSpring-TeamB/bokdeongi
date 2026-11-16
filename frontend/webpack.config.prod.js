const { merge } = require('webpack-merge');
const common = require('./webpack.common.js');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = merge(common, {
  mode: 'production',
  plugins: [
    new CopyPlugin({
      patterns: [
        { from: '*.html', to: '[name][ext]' },
        { from: 'img', to: 'img' },
        { from: 'fonts', to: 'fonts' },
        { from: 'css', to: 'css' },
        {
          from: 'js',
          to: 'js',
          globOptions: {
            ignore: ['**/app.js'],
          },
        },
      ],
    }),
  ],
});

Setup WSL
=========

[![Version][Version Badge]][Latest Release]
[![License][License Badge]][License File]

A GitHub action to install and setup a Linux distribution for the Windows Subsystem for Linux (WSL).

Beginning with `windows-2019` virtual environment for GitHub actions, WSL is enabled.
However, there is no Linux distribution installed by default and there is also no easy shell for
`run` steps that executes commands within a WSL distribution.

This action provides an easy way to install Linux distributions for WSL, update those to the latest packages and
install additional packages in them. It also provides a comfortable shell for `run` steps that uses the default
WSL distribution and distribution-specific shells if you set up multiple distributions.

Table of Contents
-----------------
* [Usage](#usage)
  * [Action](#action)
  * [Shell](#shell)
  * [Default Shell](#default-shell)
  * [Inputs](#inputs)
    * [distribution](#distribution)
    * [use-cache](#use-cache)
    * [set-as-default](#set-as-default)
    * [update](#update)
    * [additional-packages](#additional-packages)
    * [wsl-shell-command](#wsl-shell-command)
* [Version Numbers](#version-numbers)
* [License](#license)



Usage
-----

### Action

To use this action with all inputs set to their default value, just use its name.

_**Example:**_

```yaml
- uses: Vampire/setup-wsl@v1
```

This will first check whether the distribution is installed already. If not, it will be installed and also
configured as default WSL distribution. Independently of whether the distribution was installed already,
the wsl-shell wrapper for the default WSL distribution and the one for the distribution this action is running for
are rewritten to disk for later usage in `run` steps.

This action can be configured via inputs to configure the distribution as default even if it is not going to be
installed or not to configure the distribution as default even if it is going to be installed by this action.
Furthermore, inputs can be used to make this action update the installed distribution to the latest available
packages after installation, to install additional packages, and to use a different command for the wsl-shell wrapper.

### Shell

By default the generated wsl-shell wrapper is called `wsl-bash` (and `wsl-bash_<distribution id>`) and ultimately
calls the command `bash --noprofile --norc -euo pipefail '/the/generated/run_script'`. If you want a different
shell being used or different options being used, you can configure the shell command using the
[`wsl-shell-command` input](#wsl-shell-command). The name of the wrapper script is derived from the first word
in the `wsl-shell-command` input, so if you configure `ash -eu` as command, the script is named `wsl-ash`
(and `wsl-ash_<distribution id>`). Differently named wsl-shell wrappers from former action executions are not
deleted.

The wsl-shell wrapper without distribution id suffix always uses the default WSL distribution at the time
it is actually invoked. If you want to target a specific distribution, either make sure it is the default,
for example using this action, or use the distribution specific wsl-shell wrapper that always uses the
according WSL distribution it is created for.

_**Examples:**_
```yaml
- shell: wsl-bash {0}
  run: id

- shell: wsl-bash_Ubuntu-20.04 {0}
  run: |
      npm ci
      npm run build
      npm run package
```

### Default Shell

If you want to use the wsl-shell wrapper for all `run` steps (unless a `shell` is specified explicitly for it),
GitHub Actions also lets you configure the default shell for the whole workflow or for a specific job,
by using the `defaults` key on the respective level.

_**Example:**_

```yaml
defaults:
    run:
        shell: wsl-bash {0}

steps:
    - uses: Vampire/setup-wsl@v1

    - run: |
          npm ci
          npm run build
          npm run package
```

### Inputs

#### distribution

The WSL distribution to install, update, or configure.

If the distribution is not yet installed, it will be installed first.
After successful installation the distribution is also configured as default WSL distribution if not disabled
using the [`set-as-default` input](#set-as-default).

If the distribution is already installed, the default WSL distribution is not changed, except if enabled using the
`set-as-default` input.

The first installed WSL distribution is automatically the default one, independently of the `set-as-default` input.

Either way, the wsl-shell wrapper scripts are created or overwritten according to the current action configuration.

The values currently supported by this action are:
* `Debian` **(default)**
* `Alpine`
* `kali-linux`
* `openSUSE-Leap-15.2`
* `Ubuntu-20.04`
* `Ubuntu-18.04`
* `Ubuntu-16.04`

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      distribution: Ubuntu-18.04
```

#### use-cache

Whether to use the [cache][Caching dependencies to speed up workflows Website] for the downloaded distribution
installer. This saves time in subsequent runs, jobs, or workflows but consumes space from the available cache
space of the repository. Refer to [`actions/cache` documentation][actions/cache Website] for current usage limits.

**Default value:** `'true'`

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      use-cache: 'false'
```

#### set-as-default

Whether to set the distribution as default WSL distribution.
This can also be used if the distribution is installed already.

**Default value:**
* `'true'` if the distribution is going to be installed
* `'false'` if the distribution is only getting configured, updated, or additional packages installed
* the first installed WSL distribution is automatically the default one, independently of this input

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      set-as-default: 'false'
```

#### update

Whether to update the distribution after installation.
This can also be used if the distribution is installed already.

**Default value:** `'false'`

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      update: 'true'
```

#### additional-packages

Space separated list of additional packages to install after distribution installation.
This can also be used if the distribution is installed already.

**Default value:** none

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      additional-packages:
          dos2unix
          ash
```

#### wsl-shell-command

The command that is used in the wsl-shell wrapper scripts to execute the `run`-step script.
The name of the wrapper scripts will be derived from the first word in this input prefixed with `wsl-`.
This means that for the default value, the wrapper scripts will start with `wsl-bash`.
The `run`-step script will be given as additional parameter after the given string.
This can also be used if the distribution is installed already to change the wrapper scripts or generate
additional ones for other shells.

**Default value:** `bash --noprofile --norc -euo pipefail`

_**Example:**_
```yaml
- uses: Vampire/setup-wsl@v1
  with:
      wsl-shell-command: ash -eu

- shell: wsl-ash {0}
  run: id
```



Version Numbers
---------------

Versioning of this GitHub action follows the [Semantic Versioning][Semantic Versioning Website] specification.

Releases are tagged following the pattern `vX.Y.Z`. For GitHub actions it is common to also have a rolling tag
for the major version that always points at the latest release within the major version. But as the author personally
greatly dislikes rolling tags which are totally against the concept of a tag, instead a `vX` branch is provided
that will always point to the latest release within the major version. As actions can refer tags and branches
exactly the same, this will not make any difference for using the action, but it is in the authors opinion cleaner.



License
-------

```
Copyright 2020 Björn Kautler

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```



[Version Badge]:
    https://shields.javacord.org/github/v/release/Vampire/setup-wsl.svg?sort=semver&label=Version
[License Badge]:
    https://shields.javacord.org/github/license/Vampire/setup-wsl.svg?label=License

[Latest Release]:
    https://github.com/Vampire/setup-wsl/releases/latest
[License File]:
    https://github.com/Vampire/setup-wsl/blob/master/LICENSE
[Semantic Versioning Website]:
    https://semver.org
[Caching dependencies to speed up workflows Website]:
    https://docs.github.com/en/actions/configuring-and-managing-workflows/caching-dependencies-to-speed-up-workflows
[actions/cache Website]:
    https://github.com/marketplace/actions/cache

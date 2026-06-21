#!/data/data/com.termux/files/usr/bin/bash
# shellcheck shell=bash

if [ -z "${BASH_VERSION:-}" ]; then
    echo "The 'termux-apps-info-app-version-name.bash' script must be run from a 'bash' shell."; return 64 2>/dev/null|| exit 64 # EX__USAGE
fi

##
# `termux_core__bash__termux_apps_info_app_version_name__show_help`
##
termux_core__bash__termux_apps_info_app_version_name__show_help() {

    cat <<'HELP_EOF'
termux-apps-info-app-version-name.bash can be used to get/unset
variable values of the app version name environment variables
`*_APP__APP_VERSION_NAME` for Termux app `TERMUX_APP__`, its plugin
apps `TERMUX_*_APP__` and external apps `*_APP__` app scoped that
exist in the `termux-apps-info.env` file, with support for validation
of values.


Usage:
  termux-apps-info-app-version-name.bash <command> <args...>


Available commands:
    get-value                    Get app version name value from
                                 Termux scoped variable.
    unset-value                  Unset Termux scoped variable value
                                 for app version name.



get-value:
  termux-apps-info-app-version-name.bash get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode>

Available command options:
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value. By default, the
                     '--skip-sourcing-if-cur-app-var' flag is passed
                     to 'termux-apps-info-env-variable.bash' instead.
  [ --extended-validator=<validator> ]
                     The extended validator to pass to
                     'termux-scoped-env-variable.bash' for validation
                     of app version name value. By default, the value
                     must start with a number.



unset-value:
  termux-apps-info-app-version-name.bash unset-value \
    <scoped_var_scope_mode>



The `get-value` command type returns the value for the Termux scoped
environment variable generated for the app version name depending on
the variable scope passed and and the sub name as `APP_VERSION_NAME`.
The value is read from the environment if app scope of the variable
to get is the same as the app scope of the current app set in the
`$TERMUX_ENV__S_APP` environment variable, otherwise
`termux-apps-info.env` file is sourced first before reading the value
unless the `--skip-sourcing` flag is passed. If the environment
variable is not set (like in case app is not installed) or is set to a
valid app version name starting with a number (default) or matches the
custom validator passed with the `--extended-validator` argument, then
call will return with exit code `0`. If its set, but not to a valid
app version name, then the call will return with exit code
`81` (`C_EX__NOT_FOUND`).

The `unset-value` command type unsets the value of the Termux scoped
environment variable generated for the app version name by running the
`unset` command.

The `unset-value` command type is not available if executing the
`termux-apps-info-app-version-name.bash` script as that will not have
any effect on the calling process environment and is only available if
the script is sourced and the
`termux_core__bash__termux_apps_info_app_version_name` function is
called.

Check the help of `termux-apps-info-env-variable.sh` and
`termux-scoped-env-variable.sh` commands for info on the arguments for
the `get-value` and `unset-value` command types.



**See Also:**
- https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-app-version-name.md
- https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-app-version-name.bash.in
- https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_apps_info_app_version_name
.
- https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-env-variable.md
- https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-env-variable.bash.in
- https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_apps_info_env_variable
.
- https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
- https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.bash.in
- https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_scoped_env_variable
HELP_EOF

}

##
# `termux_core__bash__termux_apps_info_app_version_name__main` [`<argument...>`]
##
termux_core__bash__termux_apps_info_app_version_name__main() {

    local return_value

    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        termux_core__bash__termux_apps_info_app_version_name__show_help || return $?
        return 0
    elif [ "$1" = "--version" ]; then
        echo "termux-apps-info-app-version-name.bash version=0.4.0 org=termux project=termux-core-package"; return $?
        return 0
    elif [ "$1" = "unset-value" ]; then
        echo "The '$1' command cannot be run if executing the 'termux-apps-info-app-version-name.bash' script." 1>&2
        return 80 # C_EX__UNSUPPORTED
    else
        termux_core__bash__termux_apps_info_app_version_name "$@"
        return_value=$?
        if [ $return_value -eq 64 ]; then # EX__USAGE
            echo ""
            termux_core__bash__termux_apps_info_app_version_name__show_help
        fi
        return $return_value
    fi

}

##### TERMUX_CORE__BASH__TERMUX_SCOPED_ENV_VARIABLE replaced at build time. (START) #####

##
# Get/Set/Unset variable names and values for `TERMUX*__` and other
# scoped environment variables exported by different Termux runtime
# components, with support for fallback values and validation of
# values.
#
# The `extended_validator` logic is based on `libalteran-sh`
# `bash___shell__validate_variable_with_extended_validator()` function.
#
# **See Also:**
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_scoped_env_variable
#
#
# `termux_core__bash__termux_scoped_env_variable` `get-name` \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>`
# `termux_core__bash__termux_scoped_env_variable` `get-value` \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<extended_validator>` [`<default_values...>`]
# `termux_core__bash__termux_scoped_env_variable` `set-value` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<value_to_set>`
# `termux_core__bash__termux_scoped_env_variable` `unset-value` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>`
##
termux_core__bash__termux_scoped_env_variable() {

    local return_value

    local command_type="${1:-}"
    local command_action="${command_type%%-*}"
    [ $# -gt 0 ] && shift 1

    if [ "$command_type" = "get-name" ]; then
        local output_mode="${1:-}"
        local scoped_var_scope_mode="${2:-}"
        local scoped_var_sub_name="${3:-}"

        if [ $# -ne 3 ]; then
            echo "Invalid argument count $# for the 'get-name' command. \
The 'termux_core__bash__termux_scoped_env_variable' function expects 3 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    elif [ "$command_type" = "get-value" ]; then
        local output_mode="${1:-}"
        local scoped_var_scope_mode="${2:-}"
        local scoped_var_sub_name="${3:-}"
        local extended_validator="${4:-}"

        local validator_arg
        local validator_mode

        if [ $# -lt 4 ]; then
            echo "Invalid argument count $# for the 'get-value' command. \
The 'termux_core__bash__termux_scoped_env_variable' function expects minimum 4 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi

        # Remove args before `default_values` to be used in `var_value_cur` for loop below.
        shift 4

        case "$extended_validator" in
            '?'|'*')
                validator_arg=""
                validator_mode="$extended_validator"
                ;;
            'r+='?*|'r-='?*|'c+='?*|'c-='?*)
                validator_arg="${extended_validator:3}" # 3:end
                validator_mode="${extended_validator:0:3}" # 0:3
                ;;
            *)
                echo "The extended_validator '$extended_validator' \
argument passed to 'termux_core__bash__termux_scoped_env_variable' is not valid. \
It must either be equal to \`?\` or \`*\`, or a regex that starts with \`r+=\` or \`r-=\`, \
or a executable or function that starts with \`c+=\` or \`c-=\`." 1>&2
                return 64 # EX__USAGE
                ;;
        esac
    elif [ "$command_type" = "set-value" ]; then
        local scoped_var_scope_mode="${1:-}"
        local scoped_var_sub_name="${2:-}"
        local value_to_set="${3:-}"

        if [ $# -ne 3 ]; then
            echo "Invalid argument count $# for the 'set-value' command. \
The 'termux_core__bash__termux_scoped_env_variable' function expects 3 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    elif [ "$command_type" = "unset-value" ]; then
        local scoped_var_scope_mode="${1:-}"
        local scoped_var_sub_name="${2:-}"

        if [ $# -ne 2 ]; then
            echo "Invalid argument count $# for the 'unset-value' command. \
The 'termux_core__bash__termux_scoped_env_variable' function expects 2 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    else
        echo "The command '$command_type' passed to 'termux_core__bash__termux_scoped_env_variable' is not valid." 1>&2
        return 64 # EX__USAGE
    fi

    local i
    local is_value_valid
    local scoped_var_root_scope_name=""
    local scoped_var_scope_name=""
    local scoped_var_sub_scope_name=""
    local scoped_var_name=""
    local scoped_var_set=""
    local scoped_var_value_invalid_error_suffix=""
    local var_value_cur

    # A valid environment variable name (like `TERMUX__VAR`) or a bash array variable (like `TERMUX__ARRAY[0]`).
    local valid_bash_variable_name_regex='^[a-zA-Z_][a-zA-Z0-9_]*(\[[0-9]+\])?$'


    if [ "$command_action" = "get" ]; then
        if [ "$output_mode" != ">" ] && [ "$output_mode" != "-" ]; then
            # If `output_mode` is not a valid environment variable name.
            if [[ ! "$output_mode" =~ $valid_bash_variable_name_regex ]]; then
                echo "The output_mode '$output_mode' argument passed to \
'termux_core__bash__termux_scoped_env_variable' is not a valid environment variable name, or equal to \`>\` or \`-\`." 1>&2
                return 64 # EX__USAGE
            fi
        fi
    fi


    case "$scoped_var_scope_mode" in
        s=?*) scoped_var_scope_name="${scoped_var_scope_mode:2}";; # 2:end
        ss=?*) scoped_var_sub_scope_name="${scoped_var_scope_mode:3}";; # 3:end
        '') ;;
        cn=termux) scoped_var_sub_scope_name="_";;
        cn=termux-app) scoped_var_sub_scope_name="APP__";;
        cn=termux-api-app) scoped_var_sub_scope_name="API_APP__";;
        cn=termux-float-app) scoped_var_sub_scope_name="FLOAT_APP__";;
        cn=termux-gui-app) scoped_var_sub_scope_name="GUI_APP__";;
        cn=termux-tasker-app) scoped_var_sub_scope_name="TASKER_APP__";;
        cn=termux-widget-app) scoped_var_sub_scope_name="WIDGET_APP__";;
        cn=termux-x11-app) scoped_var_sub_scope_name="X11_APP__";;
        cn=termux-core) scoped_var_sub_scope_name="CORE__";;
        cn=termux-exec) scoped_var_sub_scope_name="EXEC__";;
        *)
            echo "The scoped_var_scope_mode '$scoped_var_scope_mode' \
argument for the variable to $command_action passed to \
'termux_core__bash__termux_scoped_env_variable' is not valid. \
It must either be a supported component name starting with \`cn=\`, \
or an environment variable scope starting with \`s=\` or \`ss=\`." 1>&2
            return 64 # EX__USAGE
            ;;
    esac


    if [ -n "$scoped_var_scope_mode" ]; then
        if [ -n "$scoped_var_scope_name" ]; then
            # Generate the full name for the variable under the provided root and sub scope.
            scoped_var_name="${scoped_var_scope_name}${scoped_var_sub_name}"
        else
            # Generate the full name for the variable under the Termux root scope and provided sub scope.
            # If `TERMUX_ENV__S_ROOT` environment variable set.
            # shellcheck disable=SC2050
            if [ -n "${TERMUX_ENV__S_ROOT:-}" ]; then
                scoped_var_root_scope_name="$TERMUX_ENV__S_ROOT"
                if [[ ! "$scoped_var_root_scope_name" =~ $valid_bash_variable_name_regex ]]; then
                    echo "The TERMUX_ENV__S_ROOT environment variable value '$scoped_var_root_scope_name' \
while running 'termux_core__bash__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
                    return 1
                fi
            # If `TERMUX_ENV__S_ROOT` placeholder got replaced during build time.
            elif [ "TERMUX_" != @"TERMUX_ENV__S_ROOT"@ ]; then
                scoped_var_root_scope_name="TERMUX_"
                if [[ ! "$scoped_var_root_scope_name" =~ $valid_bash_variable_name_regex ]]; then
                    echo "The TERMUX_ENV__S_ROOT build value '$scoped_var_root_scope_name' \
while running 'termux_core__bash__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
                    return 1
                fi
            else
                scoped_var_root_scope_name="TERMUX_"
            fi

            scoped_var_name="${scoped_var_root_scope_name}${scoped_var_sub_scope_name}${scoped_var_sub_name}"
        fi

        if [[ ! "$scoped_var_name" =~ $valid_bash_variable_name_regex ]]; then
            echo "The name of the variable to $command_action '$scoped_var_name' generated in \
'termux_core__bash__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
            return 64 # EX__USAGE
        fi


        # If command type equals `get-name`, then return the variable name and exit.
        if [ "$command_type" = "get-name" ]; then
            #echo "scoped_var_name=$scoped_var_name"
            if [ "$output_mode" = ">" ]; then
                printf "%s" "$scoped_var_name"
                return $?
            elif [ "$output_mode" != "-" ]; then
                printf -v "$output_mode" "%s" "$scoped_var_name"
                #eval "echo $output_mode=\"\${${output_mode}}\"" # Surround `${output_mode}` with `${}` in case its a base array variable name
                return $?
            else
                return 0
            fi
        elif [ "$command_type" = "set-value" ]; then
            printf -v "$scoped_var_name" "%s" "$value_to_set"
            return $?
        elif [ "$command_type" = "unset-value" ]; then
            unset "$scoped_var_name"
            return $?
        fi
    else
        if [ "$command_type" = "get-name" ] || \
            [ "$command_type" = "set-value" ] || [ "$command_type" = "unset-value" ]; then
            echo "The scoped_var_scope_mode argument for the variable \
to $command_action passed for the '$command_type' command to \
'termux_core__bash__termux_scoped_env_variable' is not set." 1>&2
            return 64 # EX__USAGE
        fi
    fi


    # If command type equals `get-value`, then find the first valid in variable name/values passed.
    if [ "$command_type" = "get-value" ]; then
        if [ "$validator_mode" = "c+=" ] || [ "$validator_mode" = "c-=" ]; then
            local command_arg_first_arg
            local -a validator_command_args=()

             # Convert command string to array.
            local -a validator_command_args=()
            termux_core__bash__set_shell_command_args_array validator_command_args \
                "extended_validator command" "$validator_arg" || return $?

            command_arg_first_arg="${validator_command_args[0]}"

            # Check if a command exists, like an executable in `$PATH`,
            # a shell function or a path to an executable.
            # - https://pubs.opengroup.org/onlinepubs/9699919799/utilities/command.html
            if ! command -v -- "$command_arg_first_arg" >/dev/null 2>&1; then
                # Check if absolute or relative path as `command -v` will only check executables under `$PATH`.
                if [[ "$command_arg_first_arg" == */* ]] && \
                        [ -f "$command_arg_first_arg" ] && [ -x "$command_arg_first_arg" ]; then
                    :
                else
                    echo "The validator command '$command_arg_first_arg' \
not found while running 'termux_core__bash__termux_scoped_env_variable' \
that is set in the extended_validator '$extended_validator' argument." 1>&2
                    return 64 # EX__USAGE
                fi
            fi
        fi

        # Loop on [`<expanded_scoped_var_name>` `<default_values>`] to
        # find the first valid value.
        i=0
        for var_value_cur in "$scoped_var_name" "$@"; do
            var_value_cur="${var_value_cur:-}"

            # If first loop, then expand the `scoped_var_name` to its value.
            if [ "$i" = 0 ] && [ -n "$scoped_var_name" ]; then
                # If mode is `*`, expand variable if it is set, or defined
                # but empty. Else if its not defined, then ignore it.
                if [ "$validator_mode" = "*" ]; then
                    eval '[ -n "${'"$scoped_var_name"'+x}" ] && scoped_var_set=1 || scoped_var_set=0'
                    if [ "$scoped_var_set" = "1" ]; then
                        var_value_cur="${!scoped_var_name:-}"
                    else
                        i=$((i + 1)); continue;
                    fi
                else
                    var_value_cur="${!scoped_var_name:-}"
                fi
            fi

            is_value_valid=0
            if [ "$validator_mode" = "r+=" ]; then
                if [[ "$var_value_cur" =~ $validator_arg ]]; then
                    is_value_valid=1
                fi
            elif [ "$validator_mode" = "r-=" ]; then
                if [[ ! "$var_value_cur" =~ $validator_arg ]]; then
                    is_value_valid=1
                fi
            elif [ "$validator_mode" = "c+=" ]; then
                # Do not use `if command; then` to preserve `set -e` failures in called function.
                return_value=0
                "${validator_command_args[@]}" "$var_value_cur" || return_value=$?
                if [ $return_value -eq 0 ]; then
                    is_value_valid=1
                else
                    is_value_valid=0 # Prevent using value overridden by called function.
                fi
            elif [ "$validator_mode" = "c-=" ]; then
                # Do not use `if ! command; then` to preserve `set -e` failures in called function.
                return_value=0
                "${validator_command_args[@]}" "$var_value_cur" || return_value=$?
                if [ $return_value -ne 0 ]; then
                    is_value_valid=1
                else
                    is_value_valid=0 # Prevent using value overridden by called function.
                fi
            else
                # If mode is `?` or `*`, and value is set,
                # or mode is `*` and value is not set.
                if [ -n "$var_value_cur" ] || [ "$validator_mode" = "*" ]; then
                    is_value_valid=1
                # Else if mode is `?`, and value is not set.
                else
                    is_value_valid=0
                fi
            fi

            if [ "$is_value_valid" = "1" ]; then
                #echo "var_value_cur=$var_value_cur"
                if [ "$output_mode" = ">" ]; then
                    printf "%s" "$var_value_cur"
                    return $?
                elif [ "$output_mode" != "-" ]; then
                    printf -v "$output_mode" "%s" "$var_value_cur"
                    #eval "echo $output_mode=\"\${${output_mode}}\"" # Surround `${output_mode}` with `${}` in case its a base array variable name
                    return $?
                else
                    return 0
                fi
            fi

            i=$((i + 1))
        done


        # If a valid value not found.

        if [ -n "$scoped_var_name" ]; then
            scoped_var_value_invalid_error_suffix=" that is read from the '\$$scoped_var_name' variable"
        fi
        if [ "$output_mode" != ">" ] && [ "$output_mode" != "-" ]; then
            # Set output variable in `output_mode` to an empty string
            # since it may already be set, as callers may try to use that
            # wrong value without checking the exit code.
            # We unset after reading the values, otherwise if
            # `scoped_var_name` is equal to output variable in
            # `output_mode`, then `scoped_var_name` would get unset before
            # its read.
            printf -v "$output_mode" "%s" "" || return $?

            echo "Failed to find a valid value to set to the '\$$output_mode' \
variable${scoped_var_value_invalid_error_suffix}." 1>&2
        else
            echo "Failed to find a valid value${scoped_var_value_invalid_error_suffix}." 1>&2
        fi
        return 81 # C_EX__NOT_FOUND
    fi

}

##
# Set a bash indexed array (`declared -a`) to shell command arguments
# defined as a string by splitting the string as normally done by the
# shell.
#
# This function is based on `libalteran-sh`
# `bash___shell__set_shell_command_args_array()`
# function, check it for more info and comments.
#
#
# `termux_core__bash__set_shell_command_args_array` \
#     `<command_args_array_variable_name>` \
#     `<command_args_label>` `<command_args_string>`
##
termux_core__bash__set_shell_command_args_array() {

    local return_value

    if [ $# -ne 3 ]; then
        echo "Invalid argument count to 'termux_core__bash__set_shell_command_args_array'"
        return 1
    fi

    local command_args_array_variable_name="$1"
    local command_args_label="$2"
    local command_args_string="$3"

    local command_arg
    local command_args_string_contains_shell_special_chars="false"
    local command_args_string_contains_space="false"
    local newline=$'\n'
    local space_containing_string_regex='[[:space:]]+'
    local shell_special_chars_containing_string_regex='[]["'\''*?!\]+'
    local xargs_command_output
    local xargs_path

    local -n  command_args_array="$command_args_array_variable_name" || return $?
    command_args_array=()


    if [[ -z "$command_args_string" ]]; then
        # Unset `nameref` to `command_args_array_variable_name`.
        unset -n  command_args_array || return $?
        return 0
    fi

    if [[ "$command_args_string" == *$'\n'* ]]; then
        # shellcheck disable=SC2028
        echo "The $command_args_label value to be \
converted to a command array cannot contain a newline '\n' character:" 1>&2
        echo "\`\`\`" 1>&2
        echo "$command_args_string" 1>&2
        echo "\`\`\`" 1>&2
        return 64 # EX__USAGE
    fi


    if [[ "$command_args_string" =~ $shell_special_chars_containing_string_regex ]]; then
        command_args_string_contains_shell_special_chars="true"
    fi
    if [[ "$command_args_string" =~ $space_containing_string_regex ]]; then
        command_args_string_contains_space="true"
    fi

    if [[ "$command_args_string_contains_shell_special_chars" == "true" ]] || \
            [[ "$command_args_string_contains_space" == "true" ]]; then

        # Use `xargs` if command args string contains whitespaces or
        # shell special characters.
        if [[ "$command_args_string_contains_shell_special_chars" == "true" ]]; then
            return_value=0
            xargs_path="$(command -v "xargs")" || return_value=$?
            if [ $return_value -ne 0 ]; then
                echo "Failed to find 'xargs' for converting $command_args_label value to a command array." 1>&2
                return $return_value
            fi

            # Android `toybox` provided `xargs` does not parse arguments
            # properly and single and double quotes around arguments
            # remain, nor do backslash escapes work.
            # Termux `findutils` package supplied `xargs` does not
            # have such issues, so we ignore the check for it only if
            # `TERMUX__PREFIX` placeholder got replaced during build time.
            # `/system/bin/xargs` may get called if current shell has
            # `/system/bin` in `$PATH` instead of or before Termux bin path.
            # shellcheck disable=SC2050
            if [[ "$xargs_path" != "/data/data/com.termux/files/usr/xargs" ]] || [[ "/data/data/com.termux/files/usr" == @"TERMUX__PREFIX"@ ]]; then
                # The logic to check if `xargs` parsing is valid is based
                # on `libalteran-sh` `bash___shell__set_is_xargs_command_args_parsing_valid()`
                # function, check it for explanation.
                return_value=0
                xargs_command_output="$(printf "%s" "'' 'arg' '' 'arg with space' 'arg surround by single quote' \"arg surround by double quote\" 'arg with '\'' single quote' 'arg with \" double quote' 'arg with \" double quote surround by single quote' \"arg with ' single quote surround by double quote\" arg\ with\ escaped\ space 'arg with backslash \\ and forward slash / surround by single quote' \"arg with backslash \\ and forward slash / surround by double quote\" \\/ \\\\ ''" | \
                    xargs -r -n1 -- printf "%s\n" && echo -n x)" || return_value=$?
                if [ $return_value -ne 0 ]; then
                    echo "Failed to check if xargs command args parsing is valid." 1>&2
                    echo "xargs_command_output=\`$xargs_command_output\`" 1>&2
                    return $return_value
                fi

                if [[ "${xargs_command_output%x}" != "${newline}arg${newline}${newline}arg with space${newline}arg surround by single quote${newline}arg surround by double quote${newline}arg with ' single quote${newline}arg with \" double quote${newline}arg with \" double quote surround by single quote${newline}arg with ' single quote surround by double quote${newline}arg with escaped space${newline}arg with backslash \ and forward slash / surround by single quote${newline}arg with backslash \\ and forward slash / surround by double quote${newline}/${newline}\\${newline}" ]]; then
                    echo "The $command_args_label value cannot be \
converted to a command array in current execution environment as it contains \
shell special characters and 'xargs' command args parsing is not valid:" 1>&2
                    echo "\`\`\`" 1>&2
                    echo "$command_args_string" 1>&2
                    echo "\`\`\`" 1>&2
                    echo "xargs path: \`$(command -v "xargs" || true)\`" 1>&2
                    echo "xargs version: \`$({ xargs --version 2>/dev/null || true; } | head -n 1)\`" 1>&2
                    return 1
                fi
            fi

            command_arg="READ_ERROR"
            return_value=0
            while IFS= read -r command_arg; return_value=$?; [[ $return_value -eq 0 ]]; do
                command_args_array+=("$command_arg")
            done < <(printf "%s\n" "$command_args_string" | \
                xargs -r -n1 -- printf "%s\n" || echo -n "CMD_ERROR")
            if [[ -n "${command_arg:-}" ]] || [ $return_value -ne 1 ]; then
                echo "Failed to create command array for \
command arguments string \`$command_args_string\`." 1>&2
                [[ -n "${command_arg:-}" ]] && echo "Error type: '$command_arg'" 1>&2
                if [[ -n ${!:-} ]]; then
                    wait "$!" || true
                fi
                return 1
            fi
        else
            # Avoid creating subshell and external call to xargs if
            # command args string contains whitespaces but does not
            # contain shell special characters and rely on whitespace
            # splitting of arguments.
            local old_ifs="$IFS"
            IFS=$' \t\n'
            # shellcheck disable=SC2206
            command_args_array=( $command_args_string )
            IFS="$old_ifs"
        fi
    else
        # Avoid creating subshell and external call to xargs if
        # command args string does not contain whitespaces or shell
        # special characters.
        command_args_array=( "$command_args_string" )
    fi

    # Unset `nameref` to `command_args_array_variable_name`.
    unset -n  command_args_array || return $?

    return 0

}

##### TERMUX_CORE__BASH__TERMUX_SCOPED_ENV_VARIABLE replaced at build time. (END) #####

##### @TERMUX_GTAIEVV_BASH__TERMUX_APPS_INFO_ENV_VARIABLE_VALUE@ replaced at build time. (START) #####

##
# Source the `termux-apps-info.env` file into the current environment
# or get variable values of Termux app `TERMUX_APP__`, its plugin apps
# `TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
# variables that exist in the `termux-apps-info.env` file, with
# support for fallback values and validation of values.
#
# **See Also:**
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-env-variable.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_apps_info_env_variable
# .
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_scoped_env_variable
#
#
# `termux_core__bash__termux_apps_info_env_variable` `source-env`
# `termux_core__bash__termux_apps_info_env_variable` `get-value` [`<command_options>`] \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<extended_validator>` [`<default_values...>`]
##
termux_core__bash__termux_apps_info_env_variable() {

    local command_type="${1:-}"
    local command_action="${command_type%%-*}"
    [ $# -gt 0 ] && shift 1

    if [ "$command_type" = "source-env" ]; then
        if [ $# -ne 0 ]; then
            echo "Invalid argument count $# for the 'source-env' command. \
The 'termux_core__bash__termux_apps_info_env_variable' function expects 0 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    elif [ "$command_type" = "get-value" ]; then
        local skip_sourcing=0
        local skip_sourcing_if_cur_app_var=0
        local ensure_sourcing=0

        if [ "${1:-}" = "--skip-sourcing" ]; then
            skip_sourcing=1
            shift 1
        elif [ "${1:-}" = "--skip-sourcing-if-cur-app-var" ]; then
            skip_sourcing_if_cur_app_var=1
            shift 1
        elif [ "${1:-}" = "--ensure-sourcing" ]; then
            ensure_sourcing=1
            shift 1
        fi

        if [ $# -lt 4 ]; then
            echo "Invalid argument count $# for the 'get-value' command. \
The 'termux_core__bash__termux_apps_info_env_variable' function expects minimum 4 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi

        local scoped_var_scope_mode="${2:-}"
        local scoped_var_sub_name="${3:-}"

        case "$scoped_var_scope_mode" in
            's='[A-Z]*'_APP__'|'ss=APP__'|'ss='[A-Z]*'_APP__'|'cn='[a-z]*'-app') :;;
            *)
            echo "The scoped_var_scope_mode '$scoped_var_scope_mode' \
argument for the variable to $command_action passed to \
'termux_core__bash__termux_apps_info_env_variable' is not valid. \
It must either be a supported component name starting with \`cn=\` and ending with '-app', \
or an environment variable scope starting with \`s=\` or \`ss=\` and ending with '_APP__'." 1>&2
                return 64 # EX__USAGE
                ;;
        esac
    else
        echo "The command '$command_type' passed to 'termux_core__bash__termux_apps_info_env_variable' is not valid." 1>&2
        return 64 # EX__USAGE
    fi


    if [ "$command_type" = "source-env" ]; then
        local termux_core__apps_info_env_file=""

        # Source the `termux-apps-info.env` file.
        # The path for the file is exported in the `$TERMUX_CORE__APPS_INFO_ENV_FILE`
        # environment variable by the Termux app running the current shell.
        termux_core__bash__termux_scoped_env_variable get-value \
            termux_core__apps_info_env_file cn="termux-core" "APPS_INFO_ENV_FILE" r+='^(()|((/[^/]+)+))$' || return $?
        if [ -n "$termux_core__apps_info_env_file" ] && [ -f "$termux_core__apps_info_env_file" ]; then
            # shellcheck disable=SC1090
            source "$termux_core__apps_info_env_file" || return $?
            return $?
        else
            return 69 # EX__UNAVAILABLE
        fi
    elif [ "$command_type" = "get-value" ]; then
        # Prefix with `app_` to prevent conflict with `termux_core__bash__termux_scoped_env_variable` argument.
        local app_scoped_var_scope_name=""
        local termux_core__apps_info_env_file=""

        # If `skip_sourcing` is enabled, then directly get the value from
        # the current environment.
        if [ "$skip_sourcing" = "1" ]; then
            termux_core__bash__termux_scoped_env_variable get-value "$@"
            return $?
        # If `skip_sourcing_if_cur_app_var` is enabled and app scope of
        # the variable to get is the same as the app scope of the current
        # app, then directly get the value from the current
        # environment.
        elif [ "$skip_sourcing_if_cur_app_var" = "1" ] && [ -n "${TERMUX_ENV__S_APP:-}" ]; then
            # Get app scope of the variable to get. If caller passed
            # `scoped_var_scope_mode` as `s=*`, then it will be returned
            # as is, otherwise if `ss=*` or `cn=*` is passed, then full
            # variable scope including Termux root scope prefix will
            # be returned. The `scoped_var_sub_name` arg is passed as
            # an empty string as we only need the app scope name.
            termux_core__bash__termux_scoped_env_variable get-name \
                app_scoped_var_scope_name "$scoped_var_scope_mode" "" || return $?

            if [ "$TERMUX_ENV__S_APP" = "$app_scoped_var_scope_name" ]; then
                termux_core__bash__termux_scoped_env_variable get-value "$@"
                return $?
            fi
        fi


        # Unset variable to get before sourcing, otherwise if the
        # `termux-apps-info.env` file does not explicitly unset it if
        # variable should not be set, then any value including empty
        # (for `validator_mode` `*`) that exists in the current environment
        # may get used.
        termux_core__bash__termux_scoped_env_variable unset-value \
            "$scoped_var_scope_mode" "$scoped_var_sub_name" || return $?


        # First source the `termux-apps-info.env` file to load the latest
        # value in the current environment before getting it.
        # The path for the file is exported in the `$TERMUX_CORE__APPS_INFO_ENV_FILE`
        # environment variable by the Termux app running the current shell.
        termux_core__bash__termux_scoped_env_variable get-value \
            termux_core__apps_info_env_file cn="termux-core" "APPS_INFO_ENV_FILE" r+='^(()|((/[^/]+)+))$' || return $?
        if [ -n "$termux_core__apps_info_env_file" ] && [ -f "$termux_core__apps_info_env_file" ]; then
            # shellcheck disable=SC1090
            source "$termux_core__apps_info_env_file" || return $?
        elif [ "$ensure_sourcing" = "1" ]; then
            return 69 # EX__UNAVAILABLE
        fi

        termux_core__bash__termux_scoped_env_variable get-value "$@"
        return $?
    fi

}

##### @TERMUX_GTAIEVV_BASH__TERMUX_APPS_INFO_ENV_VARIABLE_VALUE@ replaced at build time. (END) #####

##### @TERMUX_CORE__BASH__TERMUX_APPS_INFO_APP_VERSION_NAME@ replaced at build time. (START) #####

##
# Get/Unset variable values of the app version name environment
# variables `*_APP__APP_VERSION_NAME` for Termux app `TERMUX_APP__`,
# its plugin apps `TERMUX_*_APP__` and external apps `*_APP__` app
# scoped that exist in the `termux-apps-info.env` file, with support
# for validation of values.
#
# **See Also:**
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-app-version-name.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-app-version-name.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_apps_info_app_version_name
# .
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-env-variable.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_apps_info_env_variable
# .
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.bash.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__bash__termux_scoped_env_variable
#
#
# `termux_core__bash__termux_apps_info_app_version_name` `get-value` [`<command_options>`] \
#     `<output_mode>` `<scoped_var_scope_mode>`
# `termux_core__bash__termux_apps_info_app_version_name` `unset-value` \
#     `<scoped_var_scope_mode>`
##
termux_core__bash__termux_apps_info_app_version_name() {

    local return_value

    local command_type="${1:-}"
    [ $# -gt 0 ] && shift 1

    if [ "$command_type" = "get-value" ]; then
        local opt; local opt_arg; local OPTARG=""; local OPTIND=1;

        local extended_validator='r+=^(()|((googleplay\.)?[0-9].*))$'
        local skip_sourcing_option="--skip-sourcing-if-cur-app-var"

        if [ $# -eq 0 ]; then
            echo "No arguments passed for the 'get-value' command. \
The 'termux_core__bash__termux_apps_info_app_version_name' function expects 2 arguments." 1>&2
            return 64 # EX__USAGE
        fi

        while getopts ":-:" opt; do
            opt_arg="${OPTARG:-}"
            case "${opt}" in
                -)
                    case "${OPTARG}" in *?=*) opt_arg="${OPTARG#*=}";; *) opt_arg="";; esac
                    case "${OPTARG}" in
                        extended-validator=?*)
                            extended_validator="$opt_arg"
                            ;;
                        extended-validator | extended-validator=)
                            echo "No argument set for arg option '--${OPTARG%=*}'." 1>&2
                            return 64 # EX__USAGE
                            ;;
                        skip-sourcing)
                            skip_sourcing_option="--skip-sourcing"
                            ;;
                        skip-sourcing=*)
                            echo "Arguments not allowed for flag option '--${OPTARG%=*}': \`--${OPTARG:-}\`." 1>&2
                            return 64 # EX__USAGE
                            ;;
                        '')
                            break # End of options `--`.
                            ;;
                        *)
                            echo "Unknown option: '--${OPTARG:-}'." 1>&2
                            return 64 # EX__USAGE
                            ;;
                    esac
                    ;;
                :)
                    echo "No argument passed for arg option '-$OPTARG'." 1>&2
                    return 64 # EX__USAGE
                    ;;
                \?)
                    echo "Unknown option${OPTARG:+": '-${OPTARG:-}'"}." 1>&2
                    return 64 # EX__USAGE
                    ;;
            esac
        done
        shift $((OPTIND - 1)) # Remove already processed arguments from arguments array.

        if [ $# -lt 2 ]; then
            echo "Invalid argument count $# for the 'get-value' command. \
The 'termux_core__bash__termux_apps_info_app_version_name' function expects 2 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi

        local output_mode="${1:-}"
        local scoped_var_scope_mode="${2:-}"
    elif [ "$command_type" = "unset-value" ]; then
        local scoped_var_scope_mode="${1:-}"
    else
        echo "The command '$command_type' passed to 'termux_core__bash__termux_apps_info_app_version_name' is not valid." 1>&2
        return 64 # EX__USAGE
    fi


    if [ "$command_type" = "get-value" ]; then
        local __app_version_name=""

        if [ "$output_mode" != ">" ] && [ "$output_mode" != "-" ]; then
            # A valid environment variable name (like `TERMUX__VAR`) or a bash array variable (like `TERMUX__ARRAY[0]`).
            local valid_bash_variable_name_regex='^[a-zA-Z_][a-zA-Z0-9_]*(\[[0-9]+\])?$' # ' single quote for linter error false positive against `IFS=$'\n'`

            # If `output_mode` is not a valid environment variable name.
            if [[ ! "$output_mode" =~ $valid_bash_variable_name_regex ]]; then
                echo "The output_mode '$output_mode' argument passed to \
'termux_core__bash__termux_apps_info_app_version_name' is not a valid environment variable name, or equal to \`>\` or \`-\`." 1>&2
                return 64 # EX__USAGE
            fi
        fi


        return_value=0
        termux_core__bash__termux_apps_info_env_variable get-value \
            "$skip_sourcing_option" __app_version_name \
            "$scoped_var_scope_mode" "APP_VERSION_NAME" "$extended_validator" || return_value=$?


        # If getting version name of the Termux app but failed to get it,
        # likely due to Termux app scoped `APP_VERSION_NAME` environment
        # variable not being exported if running in Termux app version
        # `<= 0.119.0` (as `TERMUX_ENV__S_ROOT` environment variable is
        # not exported), then fallback to reading the old/deprecated
        # `TERMUX_VERSION` environment variable.
        # This may give outdated/wrong values if running in a plugin
        # app like Termux:Float app and Termux app got updated in the
        # background, as `TERMUX_VERSION` would be set to the version
        # at the time the Termux:Float shell was started, and not the
        # updated version.
        if [ $return_value -eq 0 ] && [ -z "$__app_version_name" ] && \
            { [ "$scoped_var_scope_mode" = cn="termux-app" ] || [ "$scoped_var_scope_mode" = ss="APP__" ]; } && \
            [ -z "${TERMUX_ENV__S_ROOT:-}" ]; then
            return_value=0
            termux_core__bash__termux_scoped_env_variable get-value \
                __app_version_name "" "" "$extended_validator" "${TERMUX_VERSION:-}" || return_value=$?
        fi


        # If either above commands failed.
        if [ $return_value -ne 0 ]; then
            # If a valid value not found.
            if [ $return_value -eq 81 ]; then # C_EX__NOT_FOUND
                # Set output variable in `output_mode` to an empty string
                # since it may already be set, as callers may try to use
                # that wrong value without checking the exit code.
                # We unset after reading the values, otherwise if
                # `var_to_get_name` generated in
                # `termux_core__bash__termux_scoped_env_variable()` is
                # equal to output variable in `output_mode` passed to this
                # function, then `var_to_get_name` would get unset before
                # its read.
                if [ "$output_mode" != ">" ] && [ "$output_mode" != "-" ]; then
                    printf -v "$output_mode" "%s" "" || return $?
                fi
            fi
            return $return_value
        fi


        # If a valid value found.
        if [ "$output_mode" = ">" ]; then
            printf "%s" "$__app_version_name"
            return $?
        elif [ "$output_mode" != "-" ]; then
            printf -v "$output_mode" "%s" "$__app_version_name"
            return $?
        else
            return 0
        fi
    elif [ "$command_type" = "unset-value" ]; then
        termux_core__bash__termux_scoped_env_variable unset-value \
            "$scoped_var_scope_mode" "APP_VERSION_NAME" || return $?

        if [ "$scoped_var_scope_mode" = cn="termux-app" ] || \
            [ "$scoped_var_scope_mode" = ss="APP__" ]; then
            unset TERMUX_VERSION
        fi

        return 0
    fi

}

##### @TERMUX_CORE__BASH__TERMUX_APPS_INFO_APP_VERSION_NAME@ replaced at build time. (END) #####



##
# Check if script is sourced.
# - https://stackoverflow.com/a/28776166/14686958
# - https://stackoverflow.com/a/29835459/14686958
#
# To source the `termux-apps-info-app-version-name.bash` file in `$PATH`
# (with `.` or `source` command), run the following commands.
# The `command -v` command is used to find the location of the script
# file instead of directly using the `.`/`source` command to prevent
# sourcing of a (malicious) file in the current working directory with
# the same name instead of the one in `$PATH`.
# A separate function is used to source so that arguments passed to
# calling script/function are not passed to the sourced script.
# Replace `exit` with `return` if running inside a function.
# ```shell
# source_file_from_path() { local source_file="${1:-}"; [ $# -gt 0 ] && shift 1; local source_path; if source_path="$(command -v "$source_file")" && [ -n "$source_path" ]; then source "$source_path" || return $?; else echo "Failed to find the '$source_file' file to source." 1>&2; return 1; fi; }
# source_file_from_path "termux-apps-info-app-version-name.bash" || exit $?
# ```
##

# If script is sourced, return with success, otherwise call main function.
if (return 0 2>/dev/null); then
    return 0 # EX__SUCCESS
else
    termux_core__bash__termux_apps_info_app_version_name__main "$@"
    exit $?
fi

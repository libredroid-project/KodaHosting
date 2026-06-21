#!/data/data/com.termux/files/usr/bin/sh
# shellcheck shell=sh
# shellcheck disable=SC3043

##
# `termux_core__sh__termux_apps_info_env_variable__show_help`
##
termux_core__sh__termux_apps_info_env_variable__show_help() {

    cat <<'HELP_EOF'
termux-apps-info-env-variable.sh can be used to source the
`termux-apps-info.env` file into the current environment or get
variable values of Termux app `TERMUX_APP__`, its plugin apps
`TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
variables that exist in the `termux-apps-info.env` file, with
support for fallback values and validation of values.


Usage:
  termux-apps-info-env-variable.sh <command> <args...>


Available commands:
    source-env                   Source the apps info environment.
    get-value                    Get Termux scoped variable value.



source-env:
  termux-apps-info-env-variable.sh source-env \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name>



get-value:
  termux-apps-info-env-variable.sh get-value [<command_options>] \
    <output_mode> \
    <scoped_var_scope_mode> <scoped_var_sub_name> \
    <posix_validator> [<default_values...>]

Available command options:
  [ --ensure-sourcing ]
                     Ensure sourcing of `termux-apps-info.env` file
                     before getting value.
  [ --skip-sourcing ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value.
  [ --skip-sourcing-if-cur-app-var ]
                     Skip sourcing of `termux-apps-info.env` file
                     before getting value if app scope of the variable
                     to get is the same as the app scope of the
                     current app set in the `$TERMUX_ENV__S_APP`
                     environment variable.



The `source-env` command type sources the `termux-apps-info.env` file
into the current environment. If the file does not exist, then the
call will return with exit code `69` (`EX__UNAVAILABLE`).

The `get-value` command type returns the value for the Termux scoped
environment variable generated depending on the variable scope and sub
name passed. First its value is read from the environment after
optionally sourcing the `termux-apps-info.env`  file depending on
`--*-sourcing*` flags, followed by reading the optional values
passed as `default_values`, and whichever value first matches the
`posix_validator` is returned and if no value matches, then the
call will return with exit code `81` (`C_EX__NOT_FOUND`).
If the file does not exist and `--ensure-sourcing` flag was passed,
then the call will return with exit code `69` (`EX__UNAVAILABLE`).
The `scoped_var_scope_mode` argument must be set, and for scope mode
(`s=`) and sub scope mode (`ss=`), the values must end with `_APP__`,
and for the component name mode (`cn=`), the value must end with
`-app`.

The `source-env` command type is not available if executing the
`termux-apps-info-env-variable.sh` script as that will not have any
effect on the calling process environment and is only available if
the script is sourced and the
`termux_core__sh__termux_apps_info_env_variable` function is called.

Check the help of `termux-scoped-env-variable.sh` command for info
on the arguments for the `get-value` command type.



**See Also:**
- https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-env-variable.md
- https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-env-variable.sh.in
- https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__sh__termux_apps_info_env_variable
.
- https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
- https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.sh.in
- https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__sh__termux_scoped_env_variable
HELP_EOF

}

##
# `termux_core__sh__termux_apps_info_env_variable__main` [`<argument...>`]
##
termux_core__sh__termux_apps_info_env_variable__main() {

    local return_value

    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        termux_core__sh__termux_apps_info_env_variable__show_help || return $?
        return 0
    elif [ "$1" = "--version" ]; then
        echo "termux-apps-info-env-variable.sh version=0.4.0 org=termux project=termux-core-package"; return $?
        return 0
    elif [ "$1" = "source-env" ]; then
        echo "The '$1' command cannot be run if executing the 'termux-apps-info-env-variable.sh' script." 1>&2
        return 80 # C_EX__UNSUPPORTED
    else
        termux_core__sh__termux_apps_info_env_variable "$@"
        return_value=$?
        if [ $return_value -eq 64 ]; then # EX__USAGE
            echo ""
            termux_core__sh__termux_apps_info_env_variable__show_help
        fi
        return $return_value
    fi

}

##### TERMUX_CORE__SH__TERMUX_SCOPED_ENV_VARIABLE replaced at build time. (START) #####

##
# Get/Set/Unset variable names and values for `TERMUX*__` and other
# scoped environment variables exported by different Termux runtime
# components, with support for fallback values and validation of
# values.
#
# The `posix_validator` logic is based on `libalteran-sh`
# `shell__validate_variable_with_posix_validator()` function.
#
# **See Also:**
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.sh.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__sh__termux_scoped_env_variable
#
#
# `termux_core__sh__termux_scoped_env_variable` `get-name` \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>`
# `termux_core__sh__termux_scoped_env_variable` `get-value` \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<posix_validator>` [`<default_values...>`]
# `termux_core__sh__termux_scoped_env_variable` `set-value` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<value_to_set>`
# `termux_core__sh__termux_scoped_env_variable` `unset-value` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>`
##
termux_core__sh__termux_scoped_env_variable() {

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
The 'termux_core__sh__termux_scoped_env_variable' function expects 3 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    elif [ "$command_type" = "get-value" ]; then
        local output_mode="${1:-}"
        local scoped_var_scope_mode="${2:-}"
        local scoped_var_sub_name="${3:-}"
        local posix_validator="${4:-}"

        local validator_arg
        local validator_mode

        if [ $# -lt 4 ]; then
            echo "Invalid argument count $# for the 'get-value' command. \
The 'termux_core__sh__termux_scoped_env_variable' function expects minimum 4 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi

        # Remove args before `default_values` to be used in `var_value_cur` for loop below.
        shift 4

        case "$posix_validator" in
            '?'|'*')
                validator_arg=""
                validator_mode="$posix_validator"
                ;;
            'p+='?*|'p-='?*|'c+='?*|'c-='?*)
                validator_arg="${posix_validator#???}" # 3:end
                validator_mode="${posix_validator%"$validator_arg"}" # 0:3
                ;;
            *)
                echo "The posix_validator '$posix_validator' \
argument passed to 'termux_core__sh__termux_scoped_env_variable' is not valid. \
It must either be equal to \`?\` or \`*\`, or a pattern that starts with \`p+=\` or \`p-=\`, \
or a executable or function that starts with \`c+=\` or \`c-=\`." 1>&2
                return 64 # EX__USAGE
                ;;
        esac
    elif [ "$command_type" = "set-value" ]; then
        local scoped_var_scope_mode="${1:-}"
        local scoped_var_sub_name="${2:-}"
        # shellcheck disable=SC2034
        local value_to_set="${3:-}"

        if [ $# -ne 3 ]; then
            echo "Invalid argument count $# for the 'set-value' command. \
The 'termux_core__sh__termux_scoped_env_variable' function expects 3 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    elif [ "$command_type" = "unset-value" ]; then
        local scoped_var_scope_mode="${1:-}"
        local scoped_var_sub_name="${2:-}"

        if [ $# -ne 2 ]; then
            echo "Invalid argument count $# for the 'unset-value' command. \
The 'termux_core__sh__termux_scoped_env_variable' function expects 2 arguments." 1>&2
            printf 'Arguments: %s\n' "$*" 1>&2
            return 64 # EX__USAGE
        fi
    else
        echo "The command '$command_type' passed to 'termux_core__sh__termux_scoped_env_variable' is not valid." 1>&2
        return 64 # EX__USAGE
    fi

    local i
    local is_value_valid
    local scoped_var_root_scope_name=""
    local scoped_var_scope_name=""
    local scoped_var_sub_scope_name=""
    local scoped_var_name=""
    local scoped_var_value_invalid_error_suffix=""
    local var_value_cur


    if [ "$command_action" = "get" ]; then
        if [ "$output_mode" != ">" ] && [ "$output_mode" != "-" ]; then
            # If `output_mode` is not a valid environment variable name.
            if ! termux_core__sh__is_valid_shell_variable_name "$output_mode"; then
                echo "The output_mode '$output_mode' argument passed to \
'termux_core__sh__termux_scoped_env_variable' is not a valid environment variable name, or equal to \`>\` or \`-\`." 1>&2
                return 64 # EX__USAGE
            fi
        fi
    fi


    case "$scoped_var_scope_mode" in
        s=?*) scoped_var_scope_name="${scoped_var_scope_mode#??}";; # 2:end
        ss=?*) scoped_var_sub_scope_name="${scoped_var_scope_mode#???}";; # 3:end
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
'termux_core__sh__termux_scoped_env_variable' is not valid. \
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
                if ! termux_core__sh__is_valid_shell_variable_name "$scoped_var_root_scope_name"; then
                    echo "The TERMUX_ENV__S_ROOT environment variable value '$scoped_var_root_scope_name' \
while running 'termux_core__sh__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
                    return 1
                fi
            # If `TERMUX_ENV__S_ROOT` placeholder got replaced during build time.
            elif [ "TERMUX_" != @"TERMUX_ENV__S_ROOT"@ ]; then
                scoped_var_root_scope_name="TERMUX_"
                if ! termux_core__sh__is_valid_shell_variable_name "$scoped_var_root_scope_name"; then
                    echo "The TERMUX_ENV__S_ROOT build value '$scoped_var_root_scope_name' \
while running 'termux_core__sh__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
                    return 1
                fi
            else
                scoped_var_root_scope_name="TERMUX_"
            fi

            scoped_var_name="${scoped_var_root_scope_name}${scoped_var_sub_scope_name}${scoped_var_sub_name}"
        fi

        if ! termux_core__sh__is_valid_shell_variable_name "$scoped_var_name"; then
            echo "The name of the variable to $command_action '$scoped_var_name' generated in \
'termux_core__sh__termux_scoped_env_variable' is not a valid environment variable name." 1>&2
            return 64 # EX__USAGE
        fi


        # If command type equals `get-name`, then return the variable name and exit.
        if [ "$command_type" = "get-name" ]; then
            #echo "scoped_var_name=$scoped_var_name".
            if [ "$output_mode" = ">" ]; then
                printf "%s" "$scoped_var_name"
                return $?
            elif [ "$output_mode" != "-" ]; then
                eval "$output_mode"=\"\$scoped_var_name\"
                #eval "echo $output_mode=\"\${${output_mode}}\""
                return $?
            else
                return 0
            fi
        elif [ "$command_type" = "set-value" ]; then
            eval "$scoped_var_name"=\"\$value_to_set\"
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
'termux_core__sh__termux_scoped_env_variable' is not set." 1>&2
            return 64 # EX__USAGE
        fi
    fi


    # If command type equals `get-value`, then find the first valid in variable name/values passed.
    if [ "$command_type" = "get-value" ]; then
        if [ "$validator_mode" = "c+=" ] || [ "$validator_mode" = "c-=" ]; then
            local command_arg_first_arg="${validator_arg%% *}"

            # Check if a command exists, like an executable in `$PATH`,
            # a shell function or a path to an executable.
            # - https://pubs.opengroup.org/onlinepubs/9699919799/utilities/command.html
            if ! command -v -- "$command_arg_first_arg" >/dev/null 2>&1; then
                # Check if absolute or relative path as `command -v` will only check executables under `$PATH`.
                local validator_arg_is_path=0
                case "$command_arg_first_arg" in
                    *[/]*) validator_arg_is_path=1;;
                esac

                if [ "$validator_arg_is_path" = "1" ] && [ -f "$command_arg_first_arg" ] && [ -x "$command_arg_first_arg" ]; then
                    :
                else
                    echo "The validator command '$command_arg_first_arg' \
not found while running 'termux_core__sh__termux_scoped_env_variable' \
that is set in the posix_validator '$posix_validator' argument." 1>&2
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
                    eval '[ -n "${'"$scoped_var_name"'+x}" ] && var_value_cur="$'"$scoped_var_name"'" || { i=$((i + 1)); continue; }'
                else
                    eval 'var_value_cur="${'"$scoped_var_name"':-}"'
                fi
            fi

            is_value_valid=0
            if [ "$validator_mode" = "p+=" ]; then
                return_value=0
                eval    'case "$var_value_cur" in
                            '"$validator_arg"') is_value_valid=1;;
                        esac' || return_value=$?
                if [ $return_value -ne 0 ]; then
                    echo "Failure while using a positive shell \
'case' statement to validate the variable value with the pattern '$validator_arg' \
passed to 'termux_core__sh__termux_scoped_env_variable'." 1>&2
                    return 64 # EX__USAGE
                fi
            elif [ "$validator_mode" = "p-=" ]; then
                return_value=0
                eval    'case "$var_value_cur" in
                            '"$validator_arg"') :;;
                            *) is_value_valid=1;;
                        esac' || return_value=$?
                if [ $return_value -ne 0 ]; then
                    echo "Failure while using a negative shell \
'case' statement to validate the variable value with the pattern '$validator_arg' \
passed to 'termux_core__sh__termux_scoped_env_variable'." 1>&2
                    return 64 # EX__USAGE
                fi
            elif [ "$validator_mode" = "c+=" ]; then
                # Do not use `if command; then` to preserve `set -e` failures in called function.
                return_value=0
                $validator_arg "$var_value_cur" || return_value=$?
                if [ $return_value -eq 0 ]; then
                    is_value_valid=1
                else
                    is_value_valid=0 # Prevent using value overridden by called function.
                fi
            elif [ "$validator_mode" = "c-=" ]; then
                # Do not use `if ! command; then` to preserve `set -e` failures in called function.
                return_value=0
                $validator_arg "$var_value_cur" || return_value=$?
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
                    eval "$output_mode"=\"\$var_value_cur\"
                    #eval "echo $output_mode=\"\${${output_mode}}\""
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
            eval "$output_mode"=\"\" || return $?

            echo "Failed to find a valid value to set to the '\$$output_mode' \
variable${scoped_var_value_invalid_error_suffix}." 1>&2
        else
            echo "Failed to find a valid value${scoped_var_value_invalid_error_suffix}." 1>&2
        fi
        return 81 # C_EX__NOT_FOUND
    fi

}

##
# Check if a string is a valid shell variable name.
# (like `TERMUX__VAR`).
# - https://www.gnu.org/savannah-checkouts/gnu/bash/manual/bash.html#index-name
#
#
# `termux_core__sh__is_valid_shell_variable_name` `variable_name`
##
termux_core__sh__is_valid_shell_variable_name() {

    local variable_name="${1:-}"
    local variable_name_rest="${variable_name#?}" # 1:end
    local variable_name_first_char="${variable_name%"$variable_name_rest"}" # 0:1

    case "$variable_name_first_char" in
        [a-zA-Z_])
            case "$variable_name_rest" in
                *[!a-zA-Z0-9_]*) return 1;;
                *) return 0;;
            esac;;
        *) return 1;;
    esac

}

##### TERMUX_CORE__SH__TERMUX_SCOPED_ENV_VARIABLE replaced at build time. (END) #####

##### @TERMUX_CORE__SH__TERMUX_APPS_INFO_ENV_VARIABLE@ replaced at build time. (START) #####

##
# Source the `termux-apps-info.env` file into the current environment
# or get variable values of Termux app `TERMUX_APP__`, its plugin apps
# `TERMUX_*_APP__` and external apps `*_APP__` app scoped environment
# variables that exist in the `termux-apps-info.env` file, with
# support for fallback values and validation of values.
#
# **See Also:**
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-apps-info-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-apps-info-env-variable.sh.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__sh__termux_apps_info_env_variable
# .
# - https://github.com/termux/termux-core-package/blob/master/site/pages/en/projects/docs/usage/utils/termux/shell/command/environment/termux-scoped-env-variable.md
# - https://github.com/termux/termux-core-package/blob/master/app/main/scripts/termux/shell/command/environment/termux-scoped-env-variable.sh.in
# - https://github.com/termux/termux-packages/blob/master/packages/termux-core/app/main/scripts/termux/shell/command/environment/termux_core__sh__termux_scoped_env_variable
#
#
# `termux_core__sh__termux_apps_info_env_variable` `source-env`
# `termux_core__sh__termux_apps_info_env_variable` `get-value` [`<command_options>`] \
#     `<output_mode>` \
#     `<scoped_var_scope_mode>` `<scoped_var_sub_name>` \
#     `<posix_validator>` [`<default_values...>`]
##
termux_core__sh__termux_apps_info_env_variable() {

    local command_type="${1:-}"
    local command_action="${command_type%%-*}"
    [ $# -gt 0 ] && shift 1

    if [ "$command_type" = "source-env" ]; then
        if [ $# -ne 0 ]; then
            echo "Invalid argument count $# for the 'source-env' command. \
The 'termux_core__sh__termux_apps_info_env_variable' function expects 0 arguments." 1>&2
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
The 'termux_core__sh__termux_apps_info_env_variable' function expects minimum 4 arguments." 1>&2
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
'termux_core__sh__termux_apps_info_env_variable' is not valid. \
It must either be a supported component name starting with \`cn=\` and ending with '-app', \
or an environment variable scope starting with \`s=\` or \`ss=\` and ending with '_APP__'." 1>&2
                return 64 # EX__USAGE
                ;;
        esac
    else
        echo "The command '$command_type' passed to 'termux_core__sh__termux_apps_info_env_variable' is not valid." 1>&2
        return 64 # EX__USAGE
    fi


    if [ "$command_type" = "source-env" ]; then
        local termux_core__apps_info_env_file=""

        # Source the `termux-apps-info.env` file.
        # The path for the file is exported in the `$TERMUX_CORE__APPS_INFO_ENV_FILE`
        # environment variable by the Termux app running the current shell.
        termux_core__sh__termux_scoped_env_variable get-value \
            termux_core__apps_info_env_file cn="termux-core" "APPS_INFO_ENV_FILE" p+="''|/*[!/]" || return $?
        if [ -n "$termux_core__apps_info_env_file" ] && [ -f "$termux_core__apps_info_env_file" ]; then
            # shellcheck disable=SC1090
            . "$termux_core__apps_info_env_file"
            return $?
        else
            return 69 # EX__UNAVAILABLE
        fi
    elif [ "$command_type" = "get-value" ]; then
        # Prefix with `app_` to prevent conflict with `termux_core__sh__termux_scoped_env_variable` argument.
        local app_scoped_var_scope_name=""
        local termux_core__apps_info_env_file=""

        # If `skip_sourcing` is enabled, then directly get the value from
        # the current environment.
        if [ "$skip_sourcing" = "1" ]; then
            termux_core__sh__termux_scoped_env_variable get-value "$@"
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
            termux_core__sh__termux_scoped_env_variable get-name \
                app_scoped_var_scope_name "$scoped_var_scope_mode" "" || return $?

            if [ "$TERMUX_ENV__S_APP" = "$app_scoped_var_scope_name" ]; then
                termux_core__sh__termux_scoped_env_variable get-value "$@"
                return $?
            fi
        fi


        # Unset variable to get before sourcing, otherwise if the
        # `termux-apps-info.env` file does not explicitly unset it if
        # variable should not be set, then any value including empty
        # (for `validator_mode` `*`) that exists in the current environment
        # may get used.
        termux_core__sh__termux_scoped_env_variable unset-value \
            "$scoped_var_scope_mode" "$scoped_var_sub_name" || return $?


        # First source the `termux-apps-info.env` file to load the latest
        # value in the current environment before getting it.
        # The path for the file is exported in the `$TERMUX_CORE__APPS_INFO_ENV_FILE`
        # environment variable by the Termux app running the current shell.
        termux_core__sh__termux_scoped_env_variable get-value \
            termux_core__apps_info_env_file cn="termux-core" "APPS_INFO_ENV_FILE" p+="''|/*[!/]" || return $?
        if [ -n "$termux_core__apps_info_env_file" ] && [ -f "$termux_core__apps_info_env_file" ]; then
            # shellcheck disable=SC1090
            . "$termux_core__apps_info_env_file" || return $?
        elif [ "$ensure_sourcing" = "1" ]; then
            return 69 # EX__UNAVAILABLE
        fi

        termux_core__sh__termux_scoped_env_variable get-value "$@"
        return $?
    fi

}

##### @TERMUX_CORE__SH__TERMUX_APPS_INFO_ENV_VARIABLE@ replaced at build time. (END) #####



##
# Check if script is sourced.
# - https://stackoverflow.com/a/28776166/14686958
# - https://stackoverflow.com/a/29835459/14686958
#
# To source the `termux-apps-info-env-variable.sh` file in `$PATH`
# (with `.` command), run the following commands.
# The `command -v` command is used to find the location of the script
# file instead of directly using the `.`/`source` command to prevent
# sourcing of a (malicious) file in the current working directory with
# the same name instead of the one in `$PATH`.
# A separate function is used to source so that arguments passed to
# calling script/function are not passed to the sourced script.
# Passing the `--sourcing-script` argument is necessary if sourcing
# from a `sh` shell script so that script `main` function is not run
# as there is no POSIX way to detect if current script is being
# sourced from another script as `${0##*/}` expansion in the
# "all other shells" case will contain name of the script that is
# sourcing the current script instead of the shell name.
# The `--not-sourcing-script` flag can be passed in case of executing
# the script for efficiency as that will skip the logic of checking if
# script is being sourced or not.
# Replace `exit` with `return` if running inside a function.
# ```shell
# source_file_from_path() { local source_file="${1:-}"; [ $# -gt 0 ] && shift 1; local source_path; if source_path="$(command -v "$source_file")" && [ -n "$source_path" ]; then . "$source_path" || return $?; else echo "Failed to find the '$source_file' file to source." 1>&2; return 1; fi; }
# source_file_from_path "termux-apps-info-env-variable.sh" --sourcing-script || exit $?
# ```
##
if [ "${1:-}" = "--sourcing-script" ]; then
    TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true"; shift 1;
elif [ "${1:-}" = "--not-sourcing-script" ]; then
    TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="false"; shift 1;
else
    TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="false"
    if [ -n "${ZSH_EVAL_CONTEXT:-}" ]; then
        case "$ZSH_EVAL_CONTEXT" in *:file) TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true";; esac
    elif [ -n "${KSH_VERSION:-}" ]; then
        # shellcheck disable=SC2296
        case "$KSH_VERSION" in # mksh, like on Android, will throw `${.sh.file}: bad substitution`.
            *"MIRBSD KSH"*) case "${0##*/}" in sh|ksh) TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true";; esac;;
            *) [ "$(cd "$(dirname -- "$0")" && pwd -P)/$(basename -- "$0")" != "$(cd "$(dirname -- "${.sh.file}")" && pwd -P)/$(basename -- "${.sh.file}")" ] && TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true";;
        esac
    elif [ -n "${BASH_VERSION:-}" ]; then
        (return 0 2>/dev/null) && TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true"
    else
        # For all other shells, examine `$0` for known shell binary filenames.
        # Detects `sh` and `dash`, add additional shell filenames as needed.
        case "${0##*/}" in sh|-sh|dash|-dash) TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT="true";; esac
    fi
fi

# If script is sourced, return with success, otherwise call main function.
if [ "$TERMUX_CORE__SH__TAIEV__SOURCING_SCRIPT" = "true" ]; then
    return 0 # EX__SUCCESS
else
    termux_core__sh__termux_apps_info_env_variable__main "$@"
    exit $?
fi

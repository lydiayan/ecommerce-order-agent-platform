# project overview

this is project overview.

- Language:
- Build tool:
- Framework:
- Testing Framework:

Description of the architecture

project structure describe here

The project uses `.env` for configuration.

List your backing services, such as database, message queue, external services, etc.

- Main database:
- Table Naming: Singular, prefer `account` instead of `accounts`

The project uses Maven to build and run the project, and some tasks as following:

- Build: `just build`
- Run: `just start`

For other tasks, the project uses [just](https://github.com/casey/just) as the task runner,
and the available recipes are as below:

- `just build`: build the project


- `8080`: web server listen port, and main page is http://localhost:8080